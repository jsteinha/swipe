# REQUIREMENT: python.wand imagemagick
import os
import sys
import time
import pickle
from wand.image import Image as WImage
from IPython.core import display

env = dict()
cmd = os.system

def __git_update__(machine, branch):
  cmd("ssh -t tianlins@jacob.stanford.edu \"ssh %s \'cd scr/swipe && git pull --all && git checkout %s \' \""\
                                                  %(machine, branch))

def __compile__(machine, name):
  cmd("ssh -t tianlins@jacob.stanford.edu \"ssh %s \'cd scr/swipe && python run_nlp.py --compile --name %s\' \""\
                                                  %(machine, name))

def __run__(machine, name, string, config):
  print string
  cmd("ssh -t tianlins@jacob.stanford.edu \"ssh -t %s \'cd scr/swipe && screen -dmS %s && screen -S %s -p 0 -X stuff \\\"python run_nlp.py --run %s --name %s \015 \\\" \' \""\
                                                  %(machine, string, string, config, name))
  

def __kill__(item):
  cmd("ssh -t tianlins@jacob.stanford.edu \"ssh -t %s \'cd scr/swipe && screen -S %s -p 0 -X stuff \\\" \03 exit \015 \\\"  \' \""\
                                                  %(item['machine'], item['str']))

def __fetch__(name):
  cmd("./fetch.sh %s"%name)

def __process__(name):
  cmd("./process_silent.sh %s"%name)

def fetch_all():
  for key in env.keys():
    __fetch__(key)

def process_all():
  for key in env.keys():
    try:
      os.remove('plot_%s.pdf' % key)
      os.remove('plottime_%s.pdf' % key)
    except:
      pass
    __process__(key)
    if isinstance(env[key], list):
      for item in env[key]:
        print '[%s on %s]'%(key, item['machine']), item['config']
    else:
      print '[%s on %s]'%(key, env[key]['machine']), env[key]['config']
    if os.path.exists('plot_%s.pdf' % key):
      img = WImage(filename='plot_%s.pdf' % key)
      display.display(img)
    else:
      print '[NO IMAGE]'
    if os.path.exists('plottime_%s.pdf' % key):
      img = WImage(filename='plottime_%s.pdf' % key)
      display.display(img)
    else:
      print '[NO IMAGE]'


def run(name, machine, branch, config):
  string = add(name, machine, branch, config)
  __git_update__(machine, branch)
  __compile__(machine, name)
  __run__(machine, name, string, config)

def __new_timestamp__():
  max_stamp = 0
  for item in env.values():
    if isinstance(item, list):
      for li in item:
        max_stamp = max(max_stamp, li['stamp'])
    else:
      max_stamp = max(max_stamp, item['stamp'])
  return max_stamp+1

def add(name, machine, branch, config):
  time_stamp = __new_timestamp__()
  string = name+'.'+str(time_stamp)
  env[name] = list()
  env[name].append({'machine':machine, 'branch':branch, 'config':config, 'stamp':time_stamp, 'str':string})
  return string

def kill(name):
  if isinstance(env[name], list):
    for item in env[name]:
      __kill__(item)
  else:
    __kill__(env[name])
  print 'name = ', name
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
