#!/bin/sh

# There's no need to run as root, so don't allow it, for security reasons
if [ "$USER" = "root" ]; then
    echo "Please switch to a non-root user before running this script."
    exit
fi

# Validate Java is installed and the minimum version is available
MIN_JAVA_VER='11'

if command -v java > /dev/null 2>&1; then
    # Extract Java version
    version=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
    if [[ "$version" == 1.* ]]; then
        # Java version 1.x (Java 8 or earlier)
        version_major=$(echo "$version" | awk -F '.' '{print $2}')
    else
        # Java version 9 or higher
        version_major=$(echo "$version" | awk -F '.' '{print $1}')
    fi

    if [ "$version_major" -ge "$MIN_JAVA_VER" ]; then
        echo "Passed Java version check (version $version)"
    else
        echo "Please upgrade your Java to version ${MIN_JAVA_VER} or greater"
        exit 1
    fi
else
    echo "Java is not available, please install Java ${MIN_JAVA_VER} or greater"
    exit 1
fi

# No qortal.jar but we have a Maven built one?
# Be helpful and copy across to correct location
if [ ! -e qortal.jar ] && [ -f target/qortal*.jar ]; then
    echo "Copying Maven-built Qortal JAR to correct pathname"
    cp target/qortal*.jar qortal.jar
fi

# Detect total RAM in MB
if [ "$(uname)" = "Darwin" ]; then
    # macOS
    RAM_MB=$(($(sysctl -n hw.memsize) / 1024 / 1024))
elif [ "$(uname)" = "Linux" ]; then
    # Linux
    RAM_MB=$(awk '/MemTotal/ { printf "%.0f", $2/1024 }' /proc/meminfo)
else
    echo "Unsupported operating system: $(uname)"
    exit 1
fi

echo "Detected total RAM: ${RAM_MB} MB"

# Set default JVM parameters based on RAM
if [ "$RAM_MB" -lt 2048 ]; then
    # Less than 2 GB RAM
    DEFAULT_XMS="512m"
    DEFAULT_XMX="1g"
    DEFAULT_GC="-XX:+UseSerialGC"
elif [ "$RAM_MB" -lt 4096 ]; then
    # 2 GB to 4 GB RAM
    DEFAULT_XMS="1g"
    DEFAULT_XMX="2g"
    DEFAULT_GC="-XX:+UseSerialGC"
elif [ "$RAM_MB" -lt 8192 ]; then
    # 4 GB to 8 GB RAM
    DEFAULT_XMS="2g"
    DEFAULT_XMX="4g"
    DEFAULT_GC="-XX:+UseG1GC"
elif [ "$RAM_MB" -lt 16384 ]; then
    # 8 GB to 16 GB RAM
    DEFAULT_XMS="4g"
    DEFAULT_XMX="6g"
    DEFAULT_GC="-XX:+UseG1GC"
else
    # More than 16 GB RAM
    if [ "$version_major" -ge 15 ]; then
        # Java 15 or higher, use ZGC
        DEFAULT_XMS="6g"
        DEFAULT_XMX="8g"
        DEFAULT_GC="-XX:+UseZGC"
    else
        # Java version less than 15, use G1GC
        DEFAULT_XMS="6g"
        DEFAULT_XMX="8g"
        DEFAULT_GC="-XX:+UseG1GC"
    fi
fi

# Set Metaspace size
DEFAULT_METASPACE_SIZE="256m"
DEFAULT_MAX_METASPACE_SIZE="512m"

# Set Young Generation size
DEFAULT_NEW_SIZE=""
DEFAULT_MAX_NEW_SIZE=""

# GC Logging parameters
GC_LOGGING_PARAMS="-Xlog:gc*:file=./logs/gc.log:time,uptime:filecount=10,filesize=50M"

# OutOfMemoryError handling
OOM_ERROR_PARAMS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./logs/heap_dump.hprof"
# Additional parameter, not working?
# -XX:OnOutOfMemoryError=\"kill -9 %p\""

# String deduplication and optimization
STRING_OPTS="-XX:+UseStringDeduplication -XX:+OptimizeStringConcat"

# Additional JVM parameters
ADDITIONAL_JVM_ARGS="-XX:+UseCompressedOops -Djava.net.preferIPv4Stack=false"

# Combine default JVM arguments
DEFAULT_JVM_ARGS="-Xms${DEFAULT_XMS} -Xmx${DEFAULT_XMX} ${DEFAULT_GC} -XX:MetaspaceSize=${DEFAULT_METASPACE_SIZE} -XX:MaxMetaspaceSize=${DEFAULT_MAX_METASPACE_SIZE} ${GC_LOGGING_PARAMS} ${OOM_ERROR_PARAMS} ${STRING_OPTS} ${ADDITIONAL_JVM_ARGS}"

echo "Default JVM settings:"
echo "${DEFAULT_JVM_ARGS}"

# Prompt user to accept default or input custom settings
read -p "Do you want to use these JVM settings? [Y/n]: " RESPONSE

if [ "$RESPONSE" = "n" ] || [ "$RESPONSE" = "N" ]; then
    # Prompt for custom heap sizes
    read -p "Enter initial heap size (e.g., 2g for 2 GB) [Default: ${DEFAULT_XMS}]: " CUSTOM_XMS
    read -p "Enter maximum heap size (e.g., 4g for 4 GB) [Default: ${DEFAULT_XMX}]: " CUSTOM_XMX
    CUSTOM_XMS=${CUSTOM_XMS:-$DEFAULT_XMS}
    CUSTOM_XMX=${CUSTOM_XMX:-$DEFAULT_XMX}

    # Prompt for Garbage Collector
    echo "Select Garbage Collector:"
    echo "1) Serial GC (-XX:+UseSerialGC)"
    echo "2) Parallel GC (-XX:+UseParallelGC)"
    echo "3) G1 GC (-XX:+UseG1GC)"
    if [ "$version_major" -ge 15 ]; then
        echo "4) ZGC (-XX:+UseZGC)"
    fi
    read -p "Enter choice [Default: ${DEFAULT_GC}]: " GC_CHOICE

    case $GC_CHOICE in
        1)
            CUSTOM_GC="-XX:+UseSerialGC"
            ;;
        2)
            CUSTOM_GC="-XX:+UseParallelGC"
            ;;
        3)
            CUSTOM_GC="-XX:+UseG1GC"
            ;;
        4)
            if [ "$version_major" -ge 15 ]; then
                CUSTOM_GC="-XX:+UseZGC"
            else
                echo "ZGC is not available for your Java version. Using default GC."
                CUSTOM_GC="${DEFAULT_GC}"
            fi
            ;;
        *)
            CUSTOM_GC="${DEFAULT_GC}"
            ;;
    esac

    # Prompt for Metaspace sizes
    read -p "Enter Metaspace size [Default: ${DEFAULT_METASPACE_SIZE}]: " CUSTOM_METASPACE_SIZE
    read -p "Enter Max Metaspace size [Default: ${DEFAULT_MAX_METASPACE_SIZE}]: " CUSTOM_MAX_METASPACE_SIZE
    CUSTOM_METASPACE_SIZE=${CUSTOM_METASPACE_SIZE:-$DEFAULT_METASPACE_SIZE}
    CUSTOM_MAX_METASPACE_SIZE=${CUSTOM_MAX_METASPACE_SIZE:-$DEFAULT_MAX_METASPACE_SIZE}

    # Prompt for GC Logging
    read -p "Enable GC Logging? [Y/n, Default: Enabled]: " GC_LOGGING_RESPONSE
    if [ "$GC_LOGGING_RESPONSE" = "n" ] || [ "$GC_LOGGING_RESPONSE" = "N" ]; then
        CUSTOM_GC_LOGGING_PARAMS=""
    else
        CUSTOM_GC_LOGGING_PARAMS="${GC_LOGGING_PARAMS}"
    fi

    # Prompt for OutOfMemoryError handling
    read -p "Enable Heap Dump on OutOfMemoryError? [Y/n, Default: Enabled]: " OOM_RESPONSE
    if [ "$OOM_RESPONSE" = "n" ] || [ "$OOM_RESPONSE" = "N" ]; then
        CUSTOM_OOM_ERROR_PARAMS=""
    else
        CUSTOM_OOM_ERROR_PARAMS="${OOM_ERROR_PARAMS}"
    fi

    # Prompt for String Optimization
    read -p "Enable String Deduplication and Optimization? [Y/n, Default: Enabled]: " STRING_OPT_RESPONSE
    if [ "$STRING_OPT_RESPONSE" = "n" ] || [ "$STRING_OPT_RESPONSE" = "N" ]; then
        CUSTOM_STRING_OPTS=""
    else
        CUSTOM_STRING_OPTS="${STRING_OPTS}"
    fi

    # Combine custom JVM arguments
    JVM_ARGS="-Xms${CUSTOM_XMS} -Xmx${CUSTOM_XMX} ${CUSTOM_GC} -XX:MetaspaceSize=${CUSTOM_METASPACE_SIZE} -XX:MaxMetaspaceSize=${CUSTOM_MAX_METASPACE_SIZE} ${CUSTOM_GC_LOGGING_PARAMS} ${CUSTOM_OOM_ERROR_PARAMS} ${CUSTOM_STRING_OPTS} ${ADDITIONAL_JVM_ARGS}"

else
    JVM_ARGS="${DEFAULT_JVM_ARGS}"
fi

# Create logs directory if not exists
mkdir -p logs

# Start the Qortal Core application
nohup nice -n 20 java \
    ${JVM_ARGS} \
    -jar qortal.jar \
    1>logs/run.log 2>&1 &

# Save backgrounded process's PID
echo $! > run.pid
echo "Qortal is running as PID $!"
