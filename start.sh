#!/bin/sh

# There's no need to run as root, so don't allow it, for security reasons
if [ "$USER" = "root" ]; then
	echo "Please su to non-root user before running"
	exit
fi

# Validate Java is installed and the minimum version is available
MIN_JAVA_VER='11'

if command -v java > /dev/null 2>&1; then
    # Extract Java version
    # This handles both Java 8 (1.8.0_xx) and Java 11+ (11.0.x) version formats.
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
RAM_MB=$(awk '/MemTotal/ { printf "%.0f", $2/1024 }' /proc/meminfo)
echo "Detected total RAM: ${RAM_MB} MB"

# Set default JVM parameters based on RAM
if [ "$RAM_MB" -lt 2048 ]; then
    # Less than 2 GB RAM
    DEFAULT_JVM_MEMORY_ARGS="-Xms512m -Xmx1g -XX:+UseSerialGC"
elif [ "$RAM_MB" -lt 4096 ]; then
    # 2 GB to 4 GB RAM
    DEFAULT_JVM_MEMORY_ARGS="-Xms1g -Xmx2g -XX:+UseSerialGC"
elif [ "$RAM_MB" -lt 8192 ]; then
    # 4 GB to 8 GB RAM
    DEFAULT_JVM_MEMORY_ARGS="-Xms2g -Xmx4g -XX:+UseG1GC"
elif [ "$RAM_MB" -lt 16384 ]; then
    # 8 GB to 16 GB RAM
    DEFAULT_JVM_MEMORY_ARGS="-Xms4g -Xmx6g -XX:+UseG1GC"
else
    # More than 16 GB RAM
    if [ "$version_major" -ge 15 ]; then
        # Java 15 or higher, use ZGC
        DEFAULT_JVM_MEMORY_ARGS="-Xms6g -Xmx8g -XX:+UseZGC"
    else
        # Java version less than 15, use G1GC
        DEFAULT_JVM_MEMORY_ARGS="-Xms6g -Xmx8g -XX:+UseG1GC"
    fi
fi

# Initialize additional JVM parameters
ADDITIONAL_JVM_ARGS="-XX:+HeapDumpOnOutOfMemoryError"

# Include GC-specific parameters
if echo "${DEFAULT_JVM_MEMORY_ARGS}" | grep -q "UseG1GC"; then
    ADDITIONAL_JVM_ARGS="${ADDITIONAL_JVM_ARGS} -XX:+UseStringDeduplication -XX:MaxGCPauseMillis=200"
elif echo "${DEFAULT_JVM_MEMORY_ARGS}" | grep -q "UseZGC"; then
    ADDITIONAL_JVM_ARGS="${ADDITIONAL_JVM_ARGS} -Xlog:gc"
elif echo "${DEFAULT_JVM_MEMORY_ARGS}" | grep -q "UseSerialGC"; then
    # Settings specific to Serial GC, if any
    ADDITIONAL_JVM_ARGS="${ADDITIONAL_JVM_ARGS}"
fi

# Combine JVM arguments
DEFAULT_JVM_ARGS="${DEFAULT_JVM_MEMORY_ARGS} ${ADDITIONAL_JVM_ARGS}"

echo "Default JVM settings:"
echo "${DEFAULT_JVM_ARGS}"

# Prompt user to accept default or input custom settings
read -p "Do you want to use these JVM settings? [Y/n]: " RESPONSE

if [ "$RESPONSE" = "n" ] || [ "$RESPONSE" = "N" ]; then
    echo "Enter custom JVM settings (e.g., -Xms1g -Xmx2g -XX:+UseG1GC):"
    read -p "> " JVM_ARGS
else
    JVM_ARGS="$DEFAULT_JVM_ARGS"
fi

# Although java.net.preferIPv4Stack is supposed to be false
# by default in Java 11, on some platforms (e.g. FreeBSD 12),
# it is overridden to be true by default. Hence we explicitly
# set it to false to obtain desired behaviour.
nohup nice -n 20 java \
    -Djava.net.preferIPv4Stack=false \
    ${JVM_ARGS} \
    -jar qortal.jar \
    1>run.log 2>&1 &

# Save backgrounded process's PID
echo $! > run.pid
echo "Qortal is running as PID $!"
