#!/bin/bash

matsim_home=/home/rbruno/git/matsim

qsim_events=$1
hermes_events=$2
agent=$3

cat $hermes_events | grep "=\"$agent\"" > /tmp/matsim-$agent-hermes
cat $qsim_events   | grep "=\"$agent\"" > /tmp/matsim-$agent-qsim

vimdiff /tmp/matsim-$agent-hermes /tmp/matsim-$agent-qsim
