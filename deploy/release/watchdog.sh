#!/usr/bin/env bash
# 看门狗：每 2 分钟检查一次服务器，出问题推送到微信（Server酱），能自动恢复的先自动恢复
#
#   检查一次（定时任务用）：bash watchdog.sh
#   看当前状态，不推送：    bash watchdog.sh status
#   发一条测试消息到微信：  bash watchdog.sh test
#
# 安装：.env 里填 WECHAT_SENDKEY=（Server酱的 SendKey），然后 crontab -e 加一行
#   */2 * * * * cd /opt/safework && bash watchdog.sh >> watchdog/watchdog.log 2>&1
#
# 检查项：网站首页、后端健康检查、4 个容器、磁盘、内存、数据库备份、演示数据恢复
# 推送规则（不刷屏）：问题第一次出现推一次；问题没变每 60 分钟提醒一次；恢复正常推一次；每天 9 点推一条日报
# 自动恢复：后端连续 2 次检查不通，先重启后端容器（30 分钟内最多一次），再推送
main() {
  export LC_ALL=C.UTF-8 LANG=C.UTF-8
  cd "$(dirname "$0")"
  mkdir -p watchdog
  local state=watchdog/state
  local mode="${1:-check}"

  env_get() { grep -E "^$1=" .env 2>/dev/null | tail -1 | cut -d= -f2- | sed -E 's/^["'"'"'](.*)["'"'"']$/\1/'; }
  local key port demo
  key=$(env_get WECHAT_SENDKEY)
  port=$(env_get WEB_PORT); port=${port:-80}
  demo=$(env_get DEMO_MODE)

  st_get() { grep -E "^$1=" "$state" 2>/dev/null | tail -1 | cut -d= -f2-; }
  st_set() {
    touch "$state"
    grep -vE "^$1=" "$state" > "$state.tmp" || true
    echo "$1=$2" >> "$state.tmp"
    mv "$state.tmp" "$state"
  }
  log() { echo "$(date '+%F %T') $*"; }

  # Server酱：SendKey 以 sctp 开头是 Server酱³，否则是 Server酱 Turbo
  push() {
    local title="$1" body="$2" url
    if [ -z "$key" ]; then
      log "没有配置 WECHAT_SENDKEY，只记录不推送：$title"
      return 0
    fi
    if [[ "$key" == sctp* ]]; then url="https://${key}.push.ft07.com/send"; else url="https://sctapi.ftqq.com/${key}.send"; fi
    local resp
    resp=$(curl -s --max-time 15 -X POST "$url" --data-urlencode "title=$title" --data-urlencode "desp=$body" </dev/null || echo "curl 失败")
    log "推送：$title → $(echo "$resp" | head -c 200)"
  }

  local host
  host=$(curl -s --max-time 3 http://100.100.100.200/latest/meta-data/eipv4 2>/dev/null </dev/null || true)
  [ -z "$host" ] && host=$(hostname)

  if [ "$mode" = "test" ]; then
    push "SafeWork 看门狗测试" "能收到这条消息，说明微信推送配置好了。服务器：$host，时间：$(date '+%F %T')"
    return 0
  fi

  # 正在更新、恢复演示数据时后端会停一会儿，这时不检查，免得误报、误重启
  if [ "$mode" = "check" ] && pgrep -f "^bash (demo-reset|update|start|demo-baseline)\.sh" >/dev/null; then
    log "正在更新或恢复数据，跳过本次检查"
    return 0
  fi

  # ---------- 逐项检查 ----------
  local problems=() warnings=()
  local code health
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 -k "https://127.0.0.1/" </dev/null || echo 000)
  [ "$code" = "200" ] || problems+=("网站首页打不开（HTTP $code）")

  local hcode
  health=$(curl -s --max-time 10 -w '\n%{http_code}' -k "https://127.0.0.1/api/actuator/health" </dev/null || true)
  hcode=$(echo "$health" | tail -1)
  health=$(echo "$health" | sed '$d' | tr -d '\r\n' | head -c 200)
  # 后端停了时 nginx 返回的是 502 错误网页，消息里只写状态码，不放网页代码
  echo "$health" | grep -qi '<html' && health=""
  local backend_ok=1
  if ! echo "$health" | grep -q '^{"status":"UP"'; then
    backend_ok=0
    case "$hcode" in
      502|504) problems+=("后端没有响应（HTTP $hcode）") ;;
      000|"")  problems+=("后端没有响应（连接超时）") ;;
      *)       problems+=("后端健康检查不通过（HTTP $hcode）${health:+：$health}") ;;
    esac
  fi

  local running svc
  running=$(docker compose ps --status running --services 2>/dev/null </dev/null || true)
  for svc in mysql redis backend web; do
    echo "$running" | grep -qx "$svc" || problems+=("容器 $svc 没有运行")
  done

  local disk mem
  disk=$(df -P / | awk 'NR==2 {gsub("%","",$5); print $5}')
  [ "${disk:-0}" -ge 85 ] && warnings+=("磁盘已用 ${disk}%（超过 85%），可以运行 docker system prune -f 清理旧镜像")
  mem=$(awk '/MemAvailable/ {print int($2/1024)}' /proc/meminfo)
  [ "${mem:-999}" -lt 100 ] && warnings+=("可用内存只剩 ${mem} MB")

  local latest age
  latest=$(ls -t backups/safework-*.sql.gz 2>/dev/null | head -1)
  if [ -z "$latest" ]; then
    warnings+=("没有找到数据库备份（backups/ 是空的）")
  else
    age=$(( ($(date +%s) - $(stat -c %Y "$latest")) / 3600 ))
    [ "$age" -gt 26 ] && warnings+=("最近一次数据库备份是 ${age} 小时前，每天 3 点的备份可能没执行")
  fi
  if [ "$demo" = "true" ]; then
    if [ -f demo/reset.log ]; then
      age=$(( ($(date +%s) - $(stat -c %Y demo/reset.log)) / 3600 ))
      [ "$age" -gt 26 ] && warnings+=("演示数据 ${age} 小时没有恢复过，每天 4 点的恢复可能没执行")
      tail -3 demo/reset.log | grep -q "恢复完成" || warnings+=("最近一次演示数据恢复没有成功，见 demo/reset.log")
    else
      warnings+=("演示模式已打开，但没有找到恢复日志 demo/reset.log")
    fi
  fi

  if [ "$mode" = "status" ]; then
    echo "服务器：$host"
    echo "网站首页：HTTP $code"
    echo "后端健康：HTTP $hcode ${health}"
    echo "运行中的容器：$(echo $running)"
    echo "磁盘已用：${disk}%   可用内存：${mem} MB"
    echo "最近备份：${latest:-无}"
    if [ ${#problems[@]} -eq 0 ] && [ ${#warnings[@]} -eq 0 ]; then echo "结论：一切正常"; fi
    local p
    for p in "${problems[@]}"; do echo "故障：$p"; done
    for p in "${warnings[@]}"; do echo "提醒：$p"; done
    return 0
  fi

  # ---------- 自动恢复：后端连续 2 次不通才动手（刚启动的 40 秒里也会不通） ----------
  local fails now last_restart
  fails=$(st_get backend_fails); fails=${fails:-0}
  now=$(date +%s)
  if [ "$backend_ok" = "1" ]; then
    st_set backend_fails 0
  else
    fails=$((fails + 1))
    st_set backend_fails "$fails"
    if [ "$fails" -lt 2 ]; then
      log "后端第 1 次检查不通，2 分钟后再确认"
      return 0
    else
      last_restart=$(st_get last_restart); last_restart=${last_restart:-0}
      if [ $((now - last_restart)) -lt 300 ]; then
        log "刚自动重启过，等后端启动完成，本次不推送"
        return 0
      elif [ $((now - last_restart)) -gt 1800 ]; then
        log "后端连续 $fails 次不通，自动重启"
        docker compose up -d </dev/null >/dev/null 2>&1 || true
        docker compose restart backend </dev/null >/dev/null 2>&1 || true
        st_set last_restart "$now"
        problems+=("已自动重启后端（$(date '+%H:%M')），恢复后会再推送一条【恢复】；如果 10 分钟内没收到，请登录服务器查看：docker compose logs --tail=100 backend")
      fi
    fi
  fi

  # ---------- 推送：不刷屏 ----------
  local all=("${problems[@]}" "${warnings[@]}") summary="" last_summary last_alert
  if [ ${#all[@]} -gt 0 ]; then
    summary=$(printf '%s\n' "${all[@]}" | sort | md5sum | cut -c1-12)
  fi
  last_summary=$(st_get summary)
  last_alert=$(st_get last_alert); last_alert=${last_alert:-0}

  local body title
  if [ -n "$summary" ]; then
    body=$(printf -- '- %s\n\n' "${all[@]}")
    body="服务器：$host  \n时间：$(date '+%F %T')\n\n$body"
    if [ ${#problems[@]} -gt 0 ]; then title="【故障】SafeWork：${problems[0]:0:40}"; else title="【提醒】SafeWork：${warnings[0]:0:40}"; fi
    if [ "$summary" != "$last_summary" ] || [ $((now - last_alert)) -ge 3600 ]; then
      push "$title" "$(printf '%b' "$body")"
      st_set last_alert "$now"
    else
      log "问题没变，60 分钟内不重复推送：${all[0]}"
    fi
    st_set summary "$summary"
  else
    if [ -n "$last_summary" ]; then
      push "【恢复】SafeWork 已恢复正常" "服务器：$host，时间：$(date '+%F %T')。所有检查都通过了。"
      st_set summary ""
    fi
  fi

  # ---------- 每天 9 点一条日报：知道看门狗本身还活着 ----------
  local today
  today=$(date +%F)
  if [ "$(date +%H)" = "09" ] && [ "$(st_get daily)" != "$today" ]; then
    st_set daily "$today"
    push "SafeWork 日报 $(date +%m-%d)：$([ -z "$summary" ] && echo 一切正常 || echo 有 ${#all[@]} 个问题)" \
      "$(printf '服务器：%s\n\n- 网站首页：HTTP %s\n- 后端：%s\n- 磁盘已用 %s%%，可用内存 %s MB\n- 最近备份：%s\n- 运行时间：%s' \
        "$host" "$code" "$([ "$backend_ok" = 1 ] && echo 正常 || echo 异常)" "$disk" "$mem" "${latest#backups/}" "$(uptime -p)")"
  fi

  # 日志只保留最近 3000 行
  if [ -f watchdog/watchdog.log ] && [ "$(wc -l < watchdog/watchdog.log)" -gt 3000 ]; then
    tail -2000 watchdog/watchdog.log > watchdog/watchdog.log.tmp && mv watchdog/watchdog.log.tmp watchdog/watchdog.log
  fi
  return 0
}
main "$@"
exit
