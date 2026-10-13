#!/system/bin/sh
# Only numeric Android app UIDs and module-owned chain names are accepted.
# Never flush a system chain or change a policy.
mode="$1"
uid="$2"
seconds="$3"
token="$4"
case "$uid" in ''|*[!0-9]*) echo 'ERROR invalid UID'; exit 2;; esac
case "$seconds" in ''|*[!0-9]*) echo 'ERROR invalid seconds'; exit 2;; esac
case "$token" in ''|*[!0-9a-f]*) echo 'ERROR invalid token'; exit 2;; esac
[ "${#token}" -le 8 ] || exit 2
[ "$uid" -ge 10000 ] && [ "$seconds" -le 315 ] || exit 2
[ "$((uid % 100000))" -ge 10000 ] || exit 2
chain="NDB3_${uid}_${token}"
dir="${0%/*}/ndb-state"
mkdir -p "$dir" || exit 3
chmod 700 "$dir"
cleanup_one() {
    tool="$1"
    "$tool" -w 2 -S OUTPUT >/dev/null 2>&1 || return 1
    # Delete only this exact UID/chain jump; no blanket OUTPUT flush.
    while "$tool" -w 2 -C OUTPUT -m owner --uid-owner "$uid" -j "$chain" 2>/dev/null; do
        "$tool" -w 2 -D OUTPUT -m owner --uid-owner "$uid" -j "$chain" || return 1
    done
    "$tool" -w 2 -F "$chain" 2>/dev/null || :
    "$tool" -w 2 -X "$chain" 2>/dev/null || :
    if "$tool" -w 2 -S "$chain" >/dev/null 2>&1; then return 1; fi
    return 0
}
cleanup() {
    fail=0
    cleanup_one iptables || fail=1
    cleanup_one ip6tables || fail=1
    if [ "$fail" = 0 ]; then rm -f "$dir/$chain" "$dir/$chain.ready"; fi
    return "$fail"
}
case "$mode" in
    status)
        echo 'Module firewall registry/counters:'
        for record in "$dir"/NDB3_*; do
            [ -f "$record" ] || continue
            name="${record##*/}"
            case "$name" in *.ready) continue;; esac
            case "$name" in *[!0-9a-f_NDB]*) continue;; esac
            echo "$name"
            iptables -w 2 -L "$name" -n -v 2>&1
            ip6tables -w 2 -L "$name" -n -v 2>&1
        done
        [ -f "$dir/events.log" ] && tail -n 20 "$dir/events.log"
        echo 'OK status complete'
        exit 0
        ;;
    expire)
        touch "$dir/$chain.ready" || exit 3
        sleep "$seconds"
        # A standalone root shell survives ordinary module/target app death.
        for retry in 1 2 3 4 5; do
            if cleanup; then echo "CLEANED $chain"; exit 0; fi
            sleep 1
        done
        echo "ERROR cleanup failed $chain"; exit 5
        ;;
    clear)
        # Registry names are constrained again before recursive cleanup.
        failed=0
        for record in "$dir"/NDB3_*; do
            [ -f "$record" ] || continue
            name="${record##*/}"
            case "$name" in *.ready) continue;; esac
            rest="${name#NDB3_}"
            u="${rest%_*}"
            t="${rest##*_}"
            sh "$0" remove "$u" 0 "$t" || failed=1
        done
        [ "$failed" = 0 ] && echo 'OK rules cleared'
        exit "$failed"
        ;;
    remove)
        cleanup
        exit $?
        ;;
    probe)
        command -v iptables >/dev/null && command -v ip6tables >/dev/null && command -v nohup >/dev/null || {
            echo 'ERROR missing iptables/ip6tables/nohup'; exit 4;
        }
        # Validate owner match in both families with a RETURN-only unused chain.
        touch "$dir/$chain"
        trap 'cleanup' EXIT HUP INT TERM
        for tool in iptables ip6tables; do
            "$tool" -w 2 -N "$chain" &&
            "$tool" -w 2 -A "$chain" -m owner --uid-owner "$uid" -j RETURN || {
                echo "ERROR $tool owner support unavailable"; exit 4;
            }
        done
        cleanup || { echo 'ERROR probe cleanup failed'; exit 5; }
        trap - EXIT HUP INT TERM
        echo 'OK root + IPv4/IPv6 owner match available'
        exit 0
        ;;
    block)
        [ "$seconds" -ge 1 ] && [ "$seconds" -le 300 ] || exit 2
        command -v iptables >/dev/null && command -v ip6tables >/dev/null && command -v nohup >/dev/null || {
            echo 'ERROR missing firewall tools'; exit 4;
        }
        touch "$dir/$chain" || exit 3
        # Start an independent failsafe BEFORE inserting any firewall rule.
        # Even if setup is killed, rules expire within duration + 15 seconds.
        safety=$((seconds + 15))
        nohup sh "$0" expire "$uid" "$safety" "$token" >> "$dir/events.log" 2>&1 < /dev/null &
        guard=$!
        kill -0 "$guard" 2>/dev/null || { echo 'ERROR cannot start failsafe'; cleanup; exit 5; }
        for attempt in 1 2 3; do
            [ -f "$dir/$chain.ready" ] && break
            sleep 1
        done
        [ -f "$dir/$chain.ready" ] || { echo 'ERROR failsafe not ready'; cleanup; exit 5; }
        trap 'cleanup' HUP INT TERM
        for tool in iptables ip6tables; do
            "$tool" -w 2 -N "$chain" &&
            "$tool" -w 2 -A "$chain" -j DROP &&
            "$tool" -w 2 -I OUTPUT 1 -m owner --uid-owner "$uid" -j "$chain" || {
                echo "ERROR $tool install failed; rolled back"; cleanup; exit 4;
            }
        done
        # Normal expiry begins after BOTH IPv4 and IPv6 are installed.
        nohup sh "$0" expire "$uid" "$seconds" "$token" >> "$dir/events.log" 2>&1 < /dev/null &
        timer=$!
        kill -0 "$timer" 2>/dev/null || { echo 'ERROR timer failed'; cleanup; exit 5; }
        trap - HUP INT TERM
        echo "OK blocked uid=$uid seconds=$seconds chain=$chain IPv4+IPv6"
        exit 0
        ;;
    *) echo 'ERROR invalid mode'; exit 2;;
esac
