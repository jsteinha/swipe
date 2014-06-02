#!/usr/bin/env python

from optparse import OptionParser
parser = OptionParser()
parser.add_option("--name", type="string", dest="name")
parser.add_option("--from_file", type="string", dest="from_file")
parser.add_option("--compile", action="store_true", dest="compile", default=False)
parser.add_option("--run", action="store_true", dest="run", default=False)
parser.add_option("--showRecent", type="int", dest="show_recent")
parser.add_option("--tail", type="int", dest="tail", default=5)
parser.add_option("--numThreads", type="int", dest="num_threads", default=1)
parser.add_option("--java-help", action="store_true", dest="java_help", default=False)
parser.add_option("-v", "--verbose", action="store_true", dest="verbose", default=False)
parser.add_option("-g", type="int", dest="memory", default=12)
parser.add_option("--eta", type="float", dest="eta", default=0.05)
parser.add_option("--T", type="int", dest="T")
parser.add_option("--B", type="int", dest="B")
parser.add_option("--Q", type="int", dest="Q")
parser.add_option("--K", type="int", dest="K")
parser.add_option("--Q2", type="int", dest="Q2")
parser.add_option("--K2", type="int", dest="K2")
parser.add_option("--nospaces",action="store_true",dest="nospaces",default=False)
parser.add_option("--inference", type="string", dest="inference")
parser.add_option("--learning", type="string", dest="learning")
parser.add_option("--verbosity", type="int", dest="verbosity", default=0)
parser.add_option("--numExamples", type="int", dest="num_examples")
parser.add_option("--nlpsub",action="store_true",dest="nlpsub",default=False)

(options, args) = parser.parse_args()
if not options.name:
  print "No name given, defaulting to SCRATCH"
name = options.name or "SCRATCH"

from subprocess import call, Popen
from glob import glob
import shlex
import threading
import time
include="jar/guava-14.0.1.jar:jar/fig.jar"
prefix="state/execs"

if options.from_file:
  if not options.name:
    parser.error("must specify a name if running from file")
  call(["./run.py", "--compile", "--name", options.name])
  threads = []
  for line in open(options.from_file, 'r'):
    args = shlex.split(' '.join(["./run.py --run --name", options.name, line.rstrip("\n")]))
    Popen(args)
    time.sleep(0.5)

if options.compile:
  call(["rm", "-f"] + glob("*.class"))
  call(["javac", "-cp", ".:%s" % include, "Main.java"])
  call(["mkdir", "-p", "classes/%s" % name])
  call(["mv"] + glob("*.class") + ["classes/%s/" % name])
  call(["mkdir", "-p", "%s/%s" % (prefix, name)])

if options.run:
  call_args = ["java", "-Xmx%dg" % options.memory, "-cp .:%s:classes/%s" % (include, name), 
               "Main", "-execPoolDir %s/%s" % (prefix, name)]
  if options.nlpsub:
    time.sleep(0.5)
    call_args = ["nlpsub", "-v"] + call_args
  if options.java_help:
    call_args.append("-help")
    call(shlex.split(" ".join(call_args)))
  else:
    if not options.verbose:
      call_args.append("-log.stdout false")
    call_args.append("-Main.experimentName %s" % name)
    call_args.append("-Main.eta %f" % options.eta)
    if not options.T:
      parser.error("must specify number of MCMC steps")
    call_args.append("-Main.T %d" % options.T)
    if not options.B:
      parser.error("must specify MCMC burn-in")
    call_args.append("-Main.B %d" % options.B)
    if not options.Q:
      parser.error("must specify number of training iterations")
    call_args.append("-Main.Q %d" % options.Q)
    if not options.K:
      parser.error("must specify number of random restarts")
    call_args.append("-Main.K %d" % options.K)
    if options.Q2:
      call_args.append("-Main.Q2 %d" % options.Q2)
    if options.K2:
      call_args.append("-Main.K2 %d" % options.K2)
    if options.nospaces:
      call_args.append("-Main.useSpaces false")
    if options.num_examples:
      call_args.append("-Main.numExamples %d" % options.num_examples)
    if not options.inference:
      parser.error("must specify inference algorithm to use")
    call_args.append("-Main.inference %s" % options.inference)
    if not options.learning:
      parser.error("must specify learning algorithm to use")
    call_args.append("-Main.learning %s" % options.learning)
    if options.verbosity:
      call_args.append("-Main.verbosity %d" % options.verbosity)
    call_args.append("-Main.numThreads %d" % options.num_threads)
    print 'running command: %s'  % " ".join(call_args)
    run_cmd = lambda : call(shlex.split(" ".join(call_args)))
    #if options.num_threads == 1:
    run_cmd()
    #else:
    #  threads = []
    #  for k in range(options.num_threads):
    #    thread = threading.Thread(target=run_cmd)
    #    thread.start()
    #    threads.append(thread)
    #    if k+1 < options.num_threads:
    #      time.sleep(0.5)
    #  print 'Launched %d threads' % options.num_threads
    #  for thread in threads:
    #    thread.join()
    #  print 'All threads complete'

if options.show_recent:
  dirs = glob("state/execs/%s/*.exec" % name)
  f = lambda s : int(s.split('/')[-1].split('.')[0])
  for dir in sorted(dirs, key=f)[-options.show_recent:]:
    call(shlex.split("tail -n %d %s/log" % (options.tail, dir)))
