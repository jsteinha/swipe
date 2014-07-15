# REQUIREMENT: python.wand imagemagick
import os
import sys
import time
import pickle
from wand.image import Image as WImage
from IPython.core import display

env = dict()
cmd = os.system

def __git_update__(name):
  cmd("ssh -t tianlins@jacob.stanford.edu \"ssh %s \'cd scr/swipe && git checkout %s && git pull\' \""\
                                                  %(env[name]['machine'], env[name]['branch']))

def __compile__(name):
  cmd("ssh -t tianlins@jacob.stanford.edu \"ssh %s \'cd scr/swipe && python run_nlp.py --compile --name %s\' \""\
                                                  %(env[name]['machine'], name))

def __run__(name):
  cmd("ssh -t tianlins@jacob.stanford.edu \"ssh -t %s \'cd scr/swipe && screen -dmS %s && screen -S %s -p 0 -X stuff \\\"python run_nlp.py --run %s --name %s \015 \\\" \' \""\
                                                  %(env[name]['machine'], name, name, env[name]['config'], name))
  

def __kill__(name):
  cmd("ssh -t tianlins@jacob.stanford.edu \"ssh -t %s \'cd scr/swipe && screen -S %s -p 0 -X stuff \\\" \03 exit \015 \\\"  \' \""\
                                                  %(env[name]['machine'], name))

def __fetch__(name):
  cmd("./fetch.sh %s"%name)

def __process__(name):
  cmd("./process_silent.sh %s"%name)

def fetch_all():
  for key in env.keys():
    __fetch__(key)

def process_all():
  for key in env.keys():
    __process__(key)
    print '[%s on %s]'%(key, env[key]['machine']), env[key]['config']
    img = WImage(filename='plot.pdf')
    display.display(img)


def run(name, machine, branch, config):
  add(name, machine, branch, config)
  __git_update__(name)
  __compile__(name)
  __run__(name)

def add(name, machine, branch, config):
  env[name] = {'machine':machine, 'branch':branch, 'config':config}

def kill(name):
  __kill__(name)

def save():
  fid = open('env', 'wb')
  pickle.dump(env, fid)
  fid.close()

def load():
  global env
  fid = open('env', 'rb')
  env = pickle.load(fid)
  fid.close()

if __name__ == '__main__':
  # run('remote', 'jackknife', 'master', '--T 100  --B 30 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UA --learning adagrad2')
  add('mstage_40_60', 'jackknife', 'mstage', '--T 100  --B 30 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UA --learning adagrad2')
  add('mstage_200_300', 'jude46', 'mstage', '--run --T 200 --T2 300 --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UAB --learning adagrad')
  add('mstage_T2_0xfre', 'jaclyn', 'mstage', '--run --T 500 --T2 500 --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1  --K2 50 --inference UAB --learning adagrad')
  add('baseline', 'jude46', 'master', '--run --T 500  --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UA --learning adagrad')
  add('baseline_100', 'jaclyn', 'master', '--run --T 100  --B 30 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UA --learning adagrad2')
  add('baseline_jaclyn', 'jaclyn', 'master', '--run --T 500  --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UA --learning adagrad')
  add('mstage_unshare_200_300', 'jackknife', 'mstage_unshare', '--run --T 200 --T2 300 --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1 --Q1 5  --K2 50 --inference UAB --learning adagrad')
  fetch_all()
  process_all()
  save()
  # kill('remote')
