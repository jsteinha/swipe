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
  print env[name]['str']
  cmd("ssh -t tianlins@jacob.stanford.edu \"ssh -t %s \'cd scr/swipe && screen -dmS %s && screen -S %s -p 0 -X stuff \\\"python run_nlp.py --run %s --name %s \015 \\\" \' \""\
                                                  %(env[name]['machine'], env[name]['str'], env[name]['str'], env[name]['config'], name))
  

def __kill__(name):
  cmd("ssh -t tianlins@jacob.stanford.edu \"ssh -t %s \'cd scr/swipe && screen -S %s -p 0 -X stuff \\\" \03 exit \015 \\\"  \' \""\
                                                  %(env[name]['machine'], env[name]['str']))

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

def __new_timestamp__():
  return max([0]+[item['stamp'] for item in env.values()])+1

def add(name, machine, branch, config):
  env[name] = {'machine':machine, 'branch':branch, 'config':config, 'stamp':__new_timestamp__()}
  env[name]['str'] = name+'.'+str(env[name]['stamp'])

def kill(name):
  __kill__(name)
  env.pop(name)

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
  # add('mstage_40_60', 'jackknife', 'mstage', '--T 40 --T2 60 --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UAB --learning adagrad')
  # add('mstage_200_300', 'jude46', 'mstage', '--T 200 --T2 300 --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UAB --learning adagrad')
  # add('mstage_T2_0xfre', 'jaclyn', 'mstage', '--T 500 --T2 500 --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1  --K2 50 --inference UAB --learning adagrad')
  # add('baseline_100', 'jaclyn', 'master', '--T 100  --B 30 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UA --learning adagrad2')
  # add('baseline_jaclyn', 'jaclyn', 'master', '--T 500  --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UA --learning adagrad')
  # add('mstage_unshare_200_300', 'jackknife', 'mstage_unshare', '--run --T 200 --T2 300 --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1 --Q1 5  --K2 50 --inference UAB --learning adagrad')

  load()
  run('mstage_40_60_ada2', 'jude46', 'mstage', '--T 40 --T2 60 --B 50 --Q 25 --K 5 --eta 0.4 --Q2 1 --K2 50 --inference UAB --learning adagrad2')
  # kill('baseline')
  # fetch_all()
  # process_all()
  save()
  # kill('remote')