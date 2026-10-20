"""Summarise an atrace capture of the app's main thread.

Usage: python perf/atrace_summary.py <trace.raw> <pid> [seconds]

Reads the zlib-compressed output of `atrace --async_stop -z` (written with `adb exec-out`), pairs
the begin/end marks of the main thread (tid == pid) and reports, per section name, how often it ran
and how long it took. `Choreographer#doFrame` is the frame work, `Recomposer:recompose` the Compose
recomposition and `MapPerf:*` the map marker pipeline. Main-thread busy time is the sum of the
outermost sections, which is the share of the main thread the app spent doing traced work.
"""
import re
import sys
import zlib
from collections import defaultdict

MARK = re.compile(r"^\s*(.+?)-(\d+)\s+\(.*?\)\s+\[\d+\].*?\s(\d+\.\d+):\s+tracing_mark_write:\s+([BE])\|(\d+)(?:\|(.*))?$")


def load(path):
    raw = open(path, "rb").read()
    start = raw.find(b"TRACE:\n")
    body = raw[start + len(b"TRACE:\n"):] if start >= 0 else raw
    try:
        return zlib.decompress(body).decode("utf-8", "replace")
    except zlib.error:
        return body.decode("utf-8", "replace")


def summarise(text, pid, seconds):
    stacks = defaultdict(list)
    durations = defaultdict(list)
    busy = 0.0
    first = last = None
    for line in text.splitlines():
        match = MARK.match(line)
        if not match:
            continue
        tid = int(match.group(2))
        ts = float(match.group(3))
        kind = match.group(4)
        mark_pid = int(match.group(5))
        if mark_pid != pid or tid != pid:
            continue
        first = ts if first is None else first
        last = ts
        stack = stacks[tid]
        if kind == "B":
            stack.append((match.group(6) or "?", ts))
        elif stack:
            name, begin = stack.pop()
            millis = (ts - begin) * 1000.0
            durations[name].append(millis)
            if not stack:
                busy += millis
    window = seconds or ((last - first) if first is not None else 0.0)
    print(f"window {window:.1f}s, main-thread traced busy {busy:.0f} ms "
          f"({100.0 * busy / (window * 1000.0) if window else 0:.1f}%)")
    interesting = [
        name for name in durations
        if name.startswith(("Choreographer#doFrame", "Recomposer", "MapPerf", "Compose:", "traversal", "Feed:", "Map:", "GoDex", "Alerts:",
                            "animation", "input", "RV ", "measure", "layout", "draw"))
    ]
    print(f"{'section':42} {'n':>6} {'total':>9} {'mean':>7} {'p90':>7} {'max':>7}")
    for name in sorted(interesting, key=lambda n: -sum(durations[n]))[:25]:
        values = sorted(durations[name])
        p90 = values[min(len(values) - 1, int(len(values) * 0.9))]
        print(f"{name[:42]:42} {len(values):6d} {sum(values):9.0f} {sum(values) / len(values):7.2f} "
              f"{p90:7.2f} {values[-1]:7.2f}")


if __name__ == "__main__":
    seconds = float(sys.argv[3]) if len(sys.argv) > 3 else 0.0
    summarise(load(sys.argv[1]), int(sys.argv[2]), seconds)
