#!/usr/bin/env bash

# Check for color support
if [ -t 1 ]; then
    ncolors=$( tput colors )
    if [ -n "${ncolors}" -a "${ncolors}" -ge 8 ]; then
        if normal="$( tput sgr0 )"; then
            # use terminfo names
            red="$( tput setaf 1 )"
            green="$( tput setaf 2)"
        else
            # use termcap names for FreeBSD compat
            normal="$( tput me )"
            red="$( tput AF 1 )"
            green="$( tput AF 2)"
        fi
    fi
fi

# Track the pid if we can find it
read pid 2>/dev/null <run.pid
is_pid_valid=$?

# Swap out the API port if the --testnet (or -t) argument is specified
api_port=12391
if [[ "$@" = *"--testnet"* ]] || [[  "$@" = *"-t"* ]]; then
  api_port=62391
fi

# Attempt to locate the process ID if we don't have one
if [ -z "${pid}" ]; then
  pid=$(ps aux | grep '[q]ortal.jar' | head -n 1 | awk '{print $2}')
  is_pid_valid=$?
fi

# Locate the API key if it exists
apikey=$(cat apikey.txt 2>/dev/null)
success=0

# Try and stop via the API
if [ -n "$apikey" ]; then
  echo "Stopping Qortal via API..."
  if curl --url "http://localhost:${api_port}/admin/stop?apiKey=$apikey" 1>/dev/null 2>&1; then
    success=1
  fi
fi

# Try to kill process with SIGTERM
if [ "$success" -ne 1 ] && [ -n "$pid" ]; then
  echo "Stopping Qortal process $pid..."
  if kill -15 "${pid}"; then
    success=1
  fi
fi

# Warn and exit if still no success
if [ "$success" -ne 1 ]; then
  if [ -n "$pid" ]; then
    echo "${red}Stop command failed - not running with process id ${pid}?${normal}"
  else
    echo "${red}Stop command failed - not running?${normal}"
  fi
  exit 1
fi

if [ "$success" -eq 1 ]; then
  echo "Shutdown in progress..."

  # Initialize status array
  declare -A status
  parts=("Synchronizer" "API" "Wallets" "Arbitrary TX Controllers" "Online Accounts Manager" "Transaction Importer" "Block Minter" "Networking" "Controller" "Repository" "NTP")
  data_backup_parts=("Trade Bot States" "Minting Accounts")
  for part in "${parts[@]}"; do
    status["$part"]="pending"
  done
  status["Data backup"]="pending"
  for part in "${data_backup_parts[@]}"; do
    status["$part"]="pending"
  done

  # Function to display status
  function display_status {
    echo "Shutdown in progress..."
    # Calculate max length for alignment
    local max_length=0
    for part in "${parts[@]}" "${data_backup_parts[@]}"; do
      len=${#part}
      if [ $len -gt $max_length ]; then
        max_length=$len
      fi
    done
    max_length=$((max_length + 5)) # Add length for "...  "

    for part in "${parts[@]}"; do
      printf -- "- %-*s" "$max_length" "$part..."
      if [ "${status[$part]}" = "done" ]; then
        echo "done"
      elif [ "${status[$part]}" = "failed" ]; then
        echo "FAIL"
      else
        echo ""
      fi
    done
    if [ "${status["Data backup"]}" = "in progress" ]; then
      echo "Data backup: Data backup in progress..."
    elif [ "${status["Data backup"]}" = "complete" ]; then
      echo "Data backup: Data backup complete!"
    else
      echo "Data backup:"
    fi
    for part in "${data_backup_parts[@]}"; do
      printf -- "- %-*s" "$max_length" "$part..."
      if [ "${status[$part]}" = "done" ]; then
        echo "done"
      else
        echo ""
      fi
    done
    echo
  }

  # Initial display
  display_status

  # Set up timeout
  MAX_WAIT_TIME=60
  start_time=$(date +%s)

  # Create named pipe
  PIPE_NAME="qortal_log_pipe_$$"
  mkfifo "$PIPE_NAME"
  trap "rm -f $PIPE_NAME" EXIT

  # Start tail in background
  tail -n0 -F qortal.log > "$PIPE_NAME" &
  TAIL_PID=$!

  # Open named pipe for reading
  exec 3< "$PIPE_NAME"

  # Main loop
  while true; do
    # Read from pipe with timeout
    if read -t 1 -u 3 line; then
      # Process the line
      if echo "$line" | grep -q "Shutting down synchronizer"; then
        status["Synchronizer"]="done"
      elif echo "$line" | grep -q "Shutting down API"; then
        status["API"]="done"
      elif echo "$line" | grep -q "Shutting down wallets"; then
        status["Wallets"]="done"
      elif echo "$line" | grep -q "Shutting down arbitrary-transaction controllers"; then
        status["Arbitrary TX Controllers"]="done"
      elif echo "$line" | grep -q "Shutting down online accounts manager"; then
        status["Online Accounts Manager"]="done"
      elif echo "$line" | grep -q "Shutting down transaction importer"; then
        status["Transaction Importer"]="done"
      elif echo "$line" | grep -q "Shutting down block minter"; then
        status["Block Minter"]="done"
      elif echo "$line" | grep -q "Backing up local data"; then
        status["Data backup"]="in progress"
      elif echo "$line" | grep -q "Exported sensitive/node-local data: trade bot states"; then
        status["Trade Bot States"]="done"
      elif echo "$line" | grep -q "Exported sensitive/node-local data: minting accounts"; then
        status["Minting Accounts"]="done"
        if [ "${status["Trade Bot States"]}" = "done" ] && [ "${status["Minting Accounts"]}" = "done" ]; then
          status["Data backup"]="complete"
        fi
      elif echo "$line" | grep -q "Shutting down networking"; then
        status["Networking"]="done"
      elif echo "$line" | grep -q "Shutting down controller"; then
        status["Controller"]="done"
      elif echo "$line" | grep -q "Shutting down repository"; then
        status["Repository"]="done"
      elif echo "$line" | grep -q "Shutting down NTP"; then
        status["NTP"]="done"
      elif echo "$line" | grep -q "Shutdown complete!"; then
        echo "Shutdown complete!"
        break
      elif echo "$line" | grep -q "Network threads failed to terminate"; then
        status["Networking"]="failed"
      fi

      # Clear the terminal
      clear
      # Re-display status
      display_status
    fi

    # Check for timeout
    current_time=$(date +%s)
    elapsed_time=$((current_time - start_time))
    if [ $elapsed_time -ge $MAX_WAIT_TIME ]; then
      echo "Shutdown is still pending."
      echo "Press X to force stop the process $pid, or press Enter to continue waiting."
      read -t 10 -n 1 -r user_input </dev/tty
      if [ "$user_input" = "X" ] || [ "$user_input" = "x" ]; then
        echo "Force stopping process $pid..."
        kill -9 "$pid"
        status["Networking"]="failed"
        break
      else
        echo "Continuing to wait..."
        # Reset the timer
        start_time=$current_time
      fi
    fi

    # Check if all parts are done
    all_done=true
    for part in "${parts[@]}"; do
      if [ "${status[$part]}" != "done" ] && [ "${status[$part]}" != "failed" ]; then
        all_done=false
        break
      fi
    done
    if $all_done && [ "${status["Data backup"]}" = "complete" ]; then
      echo "Shutdown complete!"
      break
    fi
  done

  # Close file descriptor
  exec 3<&-

  # Kill tail process
  kill $TAIL_PID 2>/dev/null

  # After loop
  if [ "${status["Networking"]}" = "failed" ]; then
    echo "${red}Shutdown encountered errors${normal}"
  else
    echo "${green}Qortal ended gracefully${normal}"
  fi

  rm -f run.pid
fi

exit 0
