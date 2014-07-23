#!/bin/bash

mkdir -p $1
NUM=`ssh tianlins@jacob.stanford.edu -C "ls -n ~/scr/swipe/state/execs/$1/*.exec/record | wc -l"`
for n in `seq 0 $((NUM-1))`
do
  scp tianlins@jacob.stanford.edu:~/scr/swipe/state/execs/$1/$n.exec/record $1/record-$n
  scp tianlins@jacob.stanford.edu:~/scr/swipe/state/execs/$1/$n.exec/hist $1/hist-$n
  scp tianlins@jacob.stanford.edu:~/scr/swipe/state/execs/$1/$n.exec/example $1/example-$n
done
