#!/bin/bash

mkdir -p $1
NUM=`ssh jamie -C "ls -n ~/projects/mcmc/code/swipe/state/execs/$1/*.exec/record | wc -l"`
for n in `seq 0 $((NUM-1))`
do
  scp jamie:~/projects/mcmc/code/swipe/state/execs/$1/$n.exec/record $1/record-$n
done
