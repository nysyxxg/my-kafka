# -*- coding: utf-8 -*
# 本文件应该放置在Kafka的安装主目录
import os
import time
import subprocess

startZooKeeperServerCmd = 'start bin\windows\zookeeper-server-start.bat config\zookeeper.properties'

startKafkaServerCmd = 'start bin\windows\kafka-server-start.bat config\server.properties'

print('Starting ZooKeeper Server...')
subprocess.Popen(startZooKeeperServerCmd, shell=True)
time.sleep(10)

# 启动10s后, 轮询 2181端口是否启用
print('Polling...')
startKafkaFalg = False

#每次轮询间隔5秒
interval = 5
count = 6

while count > 0:
    tmpFile = os.popen('netstat -na','r')
    breakWhileFlag = False
    for line in tmpFile.readlines():
        if line.startswith('  TCP    0.0.0.0:2181'):
            breakWhileFlag = True
            break
    print("Not yet.")
    if breakWhileFlag:
        print("It's Ok.")
        startKafkaFalg = True
        break
    else:
        count -= 1
        time.sleep(interval)

if startKafkaFalg:
    time.sleep(interval)
    print("Starting the Kafka .")
    subprocess.Popen(startKafkaServerCmd, shell=True)
else:
    print("Something wrong ...")
    input()#raw_input()
