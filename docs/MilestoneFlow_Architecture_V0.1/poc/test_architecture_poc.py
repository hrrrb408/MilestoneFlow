import base64
import hashlib
import json
import os
import secrets
import sqlite3
import tempfile
import threading
import time
import unittest
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path


def new_token() -> str:
    return base64.urlsafe_b64encode(secrets.token_bytes(32)).rstrip(b"=").decode("ascii")


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode("ascii")).hexdigest()


class PublicLinkTokenPoc(unittest.TestCase):
    def test_only_hash_is_persisted_and_link_is_single_object(self):
        token = new_token()
        self.assertGreaterEqual(len(token), 43)
        row = {
            "token_hash": token_hash(token),
            "object_type": "QUOTE_VERSION",
            "object_id": "qv-001",
            "status": "ACTIVE",
        }
        serialized = json.dumps(row)
        self.assertNotIn(token, serialized)
        self.assertEqual(token_hash(token), row["token_hash"])
        self.assertEqual("qv-001", row["object_id"])
        self.assertNotEqual(token_hash(new_token()), row["token_hash"])


class TenantIsolationPoc(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.execute("PRAGMA foreign_keys = ON")
        self.conn.executescript(
            """
            CREATE TABLE workspace(id TEXT PRIMARY KEY);
            CREATE TABLE project(
                id TEXT NOT NULL,
                workspace_id TEXT NOT NULL,
                name TEXT NOT NULL,
                PRIMARY KEY(id),
                UNIQUE(workspace_id, id),
                FOREIGN KEY(workspace_id) REFERENCES workspace(id)
            );
            CREATE TABLE quote_version(
                id TEXT PRIMARY KEY,
                workspace_id TEXT NOT NULL,
                project_id TEXT NOT NULL,
                version_no INTEGER NOT NULL,
                FOREIGN KEY(workspace_id, project_id)
                    REFERENCES project(workspace_id, id)
            );
            """
        )
        self.conn.executemany("INSERT INTO workspace(id) VALUES (?)", [("ws-a",), ("ws-b",)])
        self.conn.execute("INSERT INTO project(id, workspace_id, name) VALUES ('p-1','ws-a','A')")

    def tearDown(self):
        self.conn.close()

    def test_cross_workspace_child_reference_is_rejected(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.conn.execute(
                "INSERT INTO quote_version(id, workspace_id, project_id, version_no) VALUES (?,?,?,?)",
                ("qv-1", "ws-b", "p-1", 1),
            )

    def test_scoped_query_does_not_return_other_workspace(self):
        row = self.conn.execute(
            "SELECT id FROM project WHERE workspace_id=? AND id=?", ("ws-b", "p-1")
        ).fetchone()
        self.assertIsNone(row)


class ImmutableVersionPoc(unittest.TestCase):
    def setUp(self):
        self.conn = sqlite3.connect(":memory:")
        self.conn.executescript(
            """
            CREATE TABLE quote_version(
                id TEXT PRIMARY KEY,
                status TEXT NOT NULL,
                content TEXT NOT NULL
            );
            CREATE TRIGGER quote_version_no_update_after_publish
            BEFORE UPDATE ON quote_version
            WHEN OLD.status IN ('PUBLISHED','CONFIRMED','REJECTED','EXPIRED','REVOKED','SUPERSEDED')
            BEGIN
                SELECT RAISE(ABORT, 'IMMUTABLE_VERSION');
            END;
            CREATE TRIGGER quote_version_no_delete_after_publish
            BEFORE DELETE ON quote_version
            WHEN OLD.status <> 'DRAFT'
            BEGIN
                SELECT RAISE(ABORT, 'IMMUTABLE_VERSION');
            END;
            """
        )

    def tearDown(self):
        self.conn.close()

    def test_published_version_cannot_be_overwritten_or_deleted(self):
        self.conn.execute("INSERT INTO quote_version VALUES ('qv-1','PUBLISHED','v1')")
        with self.assertRaises(sqlite3.IntegrityError):
            self.conn.execute("UPDATE quote_version SET content='v2' WHERE id='qv-1'")
        with self.assertRaises(sqlite3.IntegrityError):
            self.conn.execute("DELETE FROM quote_version WHERE id='qv-1'")

    def test_draft_can_be_edited(self):
        self.conn.execute("INSERT INTO quote_version VALUES ('qv-d','DRAFT','a')")
        self.conn.execute("UPDATE quote_version SET content='b' WHERE id='qv-d'")
        self.assertEqual("b", self.conn.execute("SELECT content FROM quote_version WHERE id='qv-d'").fetchone()[0])


class IdempotencyPoc(unittest.TestCase):
    def test_concurrent_duplicate_requests_create_one_business_effect(self):
        fd, db_path = tempfile.mkstemp(prefix="mf-idem-", suffix=".db")
        os.close(fd)
        try:
            conn = sqlite3.connect(db_path)
            conn.execute("PRAGMA journal_mode=WAL")
            conn.executescript(
                """
                CREATE TABLE payment(
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    receivable_id TEXT NOT NULL,
                    amount TEXT NOT NULL
                );
                CREATE TABLE idempotency_record(
                    scope TEXT NOT NULL,
                    operation TEXT NOT NULL,
                    idem_key TEXT NOT NULL,
                    request_hash TEXT NOT NULL,
                    response_json TEXT NOT NULL,
                    PRIMARY KEY(scope, operation, idem_key)
                );
                """
            )
            conn.close()

            def call_once() -> dict:
                for attempt in range(20):
                    c = sqlite3.connect(db_path, timeout=1.0, isolation_level=None)
                    try:
                        c.execute("BEGIN IMMEDIATE")
                        row = c.execute(
                            "SELECT request_hash, response_json FROM idempotency_record WHERE scope=? AND operation=? AND idem_key=?",
                            ("ws-a", "PAYMENT_CREATE", "same-key"),
                        ).fetchone()
                        req_hash = hashlib.sha256(b"r-1|100.00").hexdigest()
                        if row:
                            if row[0] != req_hash:
                                raise RuntimeError("IDEMPOTENCY_KEY_REUSED")
                            c.execute("COMMIT")
                            return json.loads(row[1])
                        cur = c.execute(
                            "INSERT INTO payment(receivable_id, amount) VALUES (?,?)",
                            ("r-1", "100.00"),
                        )
                        response = {"paymentId": cur.lastrowid, "status": "CREATED"}
                        c.execute(
                            "INSERT INTO idempotency_record(scope,operation,idem_key,request_hash,response_json) VALUES (?,?,?,?,?)",
                            ("ws-a", "PAYMENT_CREATE", "same-key", req_hash, json.dumps(response)),
                        )
                        c.execute("COMMIT")
                        return response
                    except sqlite3.OperationalError as exc:
                        try:
                            c.execute("ROLLBACK")
                        except sqlite3.Error:
                            pass
                        if "locked" not in str(exc).lower() or attempt == 19:
                            raise
                        time.sleep(0.01 * (attempt + 1))
                    finally:
                        c.close()
                raise AssertionError("unreachable")

            with ThreadPoolExecutor(max_workers=8) as pool:
                results = list(pool.map(lambda _: call_once(), range(16)))

            check = sqlite3.connect(db_path)
            count = check.execute("SELECT COUNT(*) FROM payment").fetchone()[0]
            check.close()
            self.assertEqual(1, count)
            self.assertEqual(1, len({r["paymentId"] for r in results}))
        finally:
            Path(db_path).unlink(missing_ok=True)


class OutboxPoc(unittest.TestCase):
    def test_email_failure_does_not_rollback_business_publication(self):
        conn = sqlite3.connect(":memory:")
        conn.executescript(
            """
            CREATE TABLE quote_version(id TEXT PRIMARY KEY, status TEXT NOT NULL);
            CREATE TABLE outbox_event(
                id TEXT PRIMARY KEY,
                event_type TEXT NOT NULL,
                aggregate_id TEXT NOT NULL,
                status TEXT NOT NULL,
                attempts INTEGER NOT NULL DEFAULT 0
            );
            BEGIN;
            INSERT INTO quote_version VALUES ('qv-1','PUBLISHED');
            INSERT INTO outbox_event VALUES ('evt-1','QUOTE_PUBLISHED','qv-1','PENDING',0);
            COMMIT;
            """
        )
        # Simulate a provider failure. Only the asynchronous record changes.
        conn.execute("UPDATE outbox_event SET status='RETRY', attempts=attempts+1 WHERE id='evt-1'")
        self.assertEqual("PUBLISHED", conn.execute("SELECT status FROM quote_version WHERE id='qv-1'").fetchone()[0])
        self.assertEqual(("RETRY", 1), conn.execute("SELECT status, attempts FROM outbox_event WHERE id='evt-1'").fetchone())
        # Retry succeeds later.
        conn.execute("UPDATE outbox_event SET status='COMPLETED', attempts=attempts+1 WHERE id='evt-1'")
        self.assertEqual(("COMPLETED", 2), conn.execute("SELECT status, attempts FROM outbox_event WHERE id='evt-1'").fetchone())
        conn.close()


if __name__ == "__main__":
    unittest.main(verbosity=2)
