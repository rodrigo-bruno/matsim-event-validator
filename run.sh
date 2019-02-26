#!/bin/bash

script_dir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )

export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
java=$JAVA_HOME/bin/java

cd $script_dir

# build the project
mvn package -DskipTests

input=$1
if [ "$#" -ne 1 ]; then
    echo "Illegal number of parameters. Syntax: run.sh <log file>"
    exit 1
fi

rm -r temp &> /dev/null
mkdir temp &> /dev/null

cat $input | grep "ETHZ qsim event" | cut -d " " -f 4- > temp/qsim.log
cat $input | grep "ETHZ hermes event" | cut -d " " -f 4- > temp/hermes.log

# run the validator
$java -Xmx12g \
    -cp target/matsim-sim-validator-1.0-SNAPSHOT.jar \
    ch.ethz.systems.hermes.App \
    qsim $script_dir/temp/qsim.log hermes $script_dir/temp/hermes.log $script_dir/temp > $script_dir/run.log

cd - &> /dev/null

paplay /usr/share/sounds/freedesktop/stereo/complete.oga
beep
