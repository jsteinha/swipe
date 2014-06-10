#!/bin/bash

mkdir -p $1
NUM=`ssh tianlin@bouree.stanford.edu -C "ls -n ~/swipe/state/execs/$1/*.exec/record | wc -l"`
for n in `seq 0 $((NUM-1))`
do
  scp tianlin@bouree.stanford.edu:~/swipe/state/execs/$1/$n.exec/record $1/record-$n
done
