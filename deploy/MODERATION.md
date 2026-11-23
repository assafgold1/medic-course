# Moderation: friend reports

Users can report a friend from the Friends board: tap the friend, then **דיווח**. The report is saved on the server, and the reporter's account also blocks the reported user. We promised App Review that reports get reviewed **within 24 hours**.

The server runs as Pterodactyl server "Medic Course" on host `kali` (see `deploy/deploy-server.sh`). Run these on that host, where `V=/var/lib/pterodactyl/volumes/06355667-100c-4298-8782-d22eebaf1058` and the database is `$V/data/medic.db` (if not, check `MEDIC_DB` in the server's startup variables).

## See new reports

On the server, each report is also written to the log:

```bash
sudo docker logs --since 48h 06355667-100c-4298-8782-d22eebaf1058 2>&1 | grep REPORT
```

The full list of reports that haven't been handled yet:

```bash
sudo sqlite3 $V/data/medic.db "SELECT id, datetime(created_at/1000,'unixepoch'), reported_id, reported_name, reported_org, reason FROM reports WHERE handled_at IS NULL ORDER BY created_at;"
```

`reason` is `name`, `org`, `impersonation` or `other`, followed by any details the reporter typed.

## Act on a report

Rename an offending display name (organisation works the same way, using the `org` column):

```bash
sudo sqlite3 $V/data/medic.db "UPDATE users SET name='משתמש' WHERE id=<reported_id>;"
```

Remove the account. The `PRAGMA` is required: without it, sqlite3 leaves the account's sessions, progress, friends and blocks behind.

```bash
sudo sqlite3 $V/data/medic.db "PRAGMA foreign_keys=ON; DELETE FROM users WHERE id=<reported_id>;"
```

Mark the report as handled:

```bash
sudo sqlite3 $V/data/medic.db "UPDATE reports SET handled_at=strftime('%s','now')*1000 WHERE id=<report id>;"
```
