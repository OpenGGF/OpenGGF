#!/usr/bin/env python3
"""Update and restart an OpenGGF multiplayer master that runs under CubeCoders AMP.

Inputs: AMP_URL (HTTPS panel origin), AMP_USERNAME and AMP_PASSWORD from the
environment, never arguments; --instance-id of the AMP instance; optional
--endpoint wss:// URL whose TLS WebSocket upgrade must succeed afterwards.
The instance uses the openggf-multi template from
github.com/OpenGGF/openggf-amp-templates, whose update stage downloads the
branch's rolling prerelease. Upload that asset before running this script.
Origin: multiplayer testbed deployment, 2026-11-01
(docs/architecture/plans/2026-11-01-multiplayer-testbed-deployment.md).
"""
import argparse
import base64
import http.client
import json
import os
import ssl
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

# AMP application states (Core/GetStatus "State").
STOPPED, READY, FAILED = 0, 20, 100
STATE_NAMES = {0: "Stopped", 10: "PreStart", 20: "Ready", 30: "Restarting", 40: "Stopping",
               60: "Waiting", 70: "Installing", 75: "Updating", 100: "Failed"}


class AmpError(RuntimeError):
    pass


class Amp:
    def __init__(self, base, timeout=60):
        self.base = base.rstrip("/")
        self.timeout = timeout

    def call(self, path, body=None, session=None):
        request = urllib.request.Request(
            f"{self.base}/API/{path}", data=json.dumps(body or {}).encode(), method="POST",
            headers={"Accept": "application/json", "Content-Type": "application/json",
                     **({"Authorization": f"Bearer {session}"} if session else {})})
        try:
            with urllib.request.urlopen(request, timeout=self.timeout) as response:
                text = response.read().decode()
        except urllib.error.HTTPError as error:
            raise AmpError(f"{path}: HTTP {error.code}") from None
        data = json.loads(text) if text.strip() else None
        if isinstance(data, dict) and "Title" in data and "Message" in data:
            raise AmpError(f"{path}: {data['Title']}: {data['Message']}")
        return data


def login(amp, username, password, token=""):
    result = amp.call("Core/Login", {"username": username, "password": password,
                                     "token": token, "rememberMe": False})
    if not result or not result.get("success"):
        raise AmpError(f"login failed: {(result or {}).get('resultReason', 'no response')}")
    return result["sessionID"]


def state(amp, session):
    return amp.call("Core/GetStatus", session=session)["State"]


def console_tail(amp, session, lines=30):
    entries = amp.call("Core/GetUpdates", session=session).get("ConsoleEntries") or []
    return [entry.get("Contents") or "" for entry in entries][-lines:]


def wait_for(amp, session, done, deadline, label):
    seen = None
    while time.monotonic() < deadline:
        current = state(amp, session)
        if current != seen:
            print(f"  {label}: {STATE_NAMES.get(current, current)}", flush=True)
            seen = current
        if current == FAILED:
            raise AmpError(f"{label}: application failed")
        if done(current):
            return current
        time.sleep(2)
    raise AmpError(f"{label}: timed out in state {STATE_NAMES.get(seen, seen)}")


def update_tasks(updates):
    return [task for task in updates.get("Tasks") or [] if task.get("Origin") == "Core.UpdateApplication"
            or task.get("Name") == "Running Update Tasks" or task.get("Origin") == "Core.GetUpdates"]


def task_ids(amp, session):
    """Update tasks already listed; AMP keeps failed ones until dismissed, so they are not ours."""
    return {task.get("Id") for task in update_tasks(amp.call("Core/GetUpdates", session=session))}


def wait_for_update(amp, session, deadline, stale_ids, grace_s=30):
    """UpdateApplication returns at once; wait for the update tasks it starts to end.

    AMP lists an ended task until it is dismissed, so finished tasks carry an EndTime and
    report success in Status. Tasks listed before the update began are ignored: a failed
    download left in the list would otherwise hold this wait until it timed out.
    """
    started = time.monotonic()
    seen = False
    while time.monotonic() < deadline:
        updates = amp.call("Core/GetUpdates", session=session)
        for line in updates.get("ConsoleEntries") or []:
            if line.get("Contents"):
                print(f"  | {line['Contents']}", flush=True)
        tasks = [task for task in update_tasks(updates) if task.get("Id") not in stale_ids]
        seen = seen or bool(tasks)
        failed = [task for task in tasks if task.get("State") == 5
                  or (task.get("EndTime") and task.get("Status") is False)]
        if failed:
            task = failed[0]
            raise AmpError(f"update failed at {task.get('Speed') or task.get('Name')}: "
                           f"{task.get('Reason') or task.get('StateMessage')}")
        running = [task for task in tasks if not task.get("EndTime")]
        if not running and (seen or time.monotonic() - started > grace_s) \
                and state(amp, session) == STOPPED:
            return
        time.sleep(2)
    raise AmpError("update timed out")


def update(amp, session, deadline, attempts=3):
    """Run the update stages, retrying a failure such as a transient download error."""
    for attempt in range(1, attempts + 1):
        stale_ids = task_ids(amp, session)
        print("updating" + (f" (attempt {attempt})" if attempt > 1 else ""), flush=True)
        amp.call("Core/UpdateApplication", session=session)
        try:
            wait_for_update(amp, session, deadline, stale_ids)
            return
        except AmpError as error:
            if attempt == attempts or "timed out" in str(error):
                raise
            print(f"  {error}; retrying", flush=True)
            time.sleep(10)


def check_endpoint(url, timeout=15):
    """Require a certificate-validated TLS WebSocket upgrade (HTTP 101)."""
    parsed = urllib.parse.urlsplit(url)
    if parsed.scheme != "wss" or not parsed.hostname:
        raise AmpError(f"endpoint must be a wss:// URL: {url}")
    connection = http.client.HTTPSConnection(parsed.hostname, parsed.port or 443, timeout=timeout,
                                             context=ssl.create_default_context())
    key = base64.b64encode(os.urandom(16)).decode()
    connection.request("GET", parsed.path or "/", headers={
        "Connection": "Upgrade", "Upgrade": "websocket", "Sec-WebSocket-Version": "13",
        "Sec-WebSocket-Key": key})
    status = connection.getresponse().status
    connection.close()
    if status != 101:
        raise AmpError(f"{url}: expected WebSocket upgrade 101, got {status}")


def deploy(amp, username, password, instance_id, endpoint, timeout_s):
    deadline = time.monotonic() + timeout_s
    ads = login(amp, username, password)
    instance = None
    try:
        managed = amp.call("ADSModule/ManageInstance", {"InstanceId": instance_id}, ads)
        if not managed or not managed.get("Status"):
            raise AmpError(f"ManageInstance refused: {(managed or {}).get('Reason')}")
        inst = Amp(f"{amp.base}/API/ADSModule/Servers/{instance_id}", amp.timeout)
        instance = (inst, login(inst, username, "", managed["Result"]))
        api, session = instance
        print(f"instance {instance_id}: {STATE_NAMES.get(state(api, session))}", flush=True)
        if state(api, session) != STOPPED:
            api.call("Core/Stop", session=session)
            wait_for(api, session, lambda s: s == STOPPED, deadline, "stop")
        update(api, session, deadline)
        print("starting", flush=True)
        api.call("Core/Start", session=session)
        try:
            wait_for(api, session, lambda s: s == READY, deadline, "start")
            if endpoint:
                # A master that dies during startup returns to Stopped; give it time to bind.
                for attempt in range(10):
                    try:
                        check_endpoint(endpoint)
                        break
                    except (OSError, AmpError):
                        if attempt == 9 or state(api, session) != READY:
                            raise
                        time.sleep(3)
                print(f"endpoint {endpoint}: WebSocket upgrade OK", flush=True)
        except Exception:
            print("console tail:", *console_tail(api, session), sep="\n  ", file=sys.stderr)
            raise
    finally:
        for api, session in ([instance] if instance else []) + [(amp, ads)]:
            try:
                api.call("Core/Logout", session=session)
            except (AmpError, OSError):
                pass


def main():
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--instance-id", required=True)
    parser.add_argument("--endpoint", help="wss:// URL that must accept a WebSocket upgrade afterwards")
    parser.add_argument("--timeout", type=int, default=600, help="overall seconds (default 600)")
    args = parser.parse_args()
    env = {name: os.environ.get(name, "") for name in ("AMP_URL", "AMP_USERNAME", "AMP_PASSWORD")}
    missing = [name for name, value in env.items() if not value]
    if missing:
        parser.error("missing environment: " + ", ".join(missing))
    if urllib.parse.urlsplit(env["AMP_URL"]).scheme != "https":
        parser.error("AMP_URL must use https; the password must not cross the network in clear text")
    try:
        deploy(Amp(env["AMP_URL"]), env["AMP_USERNAME"], env["AMP_PASSWORD"],
               args.instance_id, args.endpoint, args.timeout)
    except (AmpError, OSError) as error:
        print(f"deploy failed: {error}", file=sys.stderr)
        return 1
    print("deployed", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
