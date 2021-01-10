# -*- coding: utf-8 -*
# 本文件应该放置在Kafka的安装主目录
import os, time, subprocess

startZooKeeperServerCmd = 'start bin\windows\zookeeper-server-start.bat config\zookeeper.properties'

startKafkaServerCmd = 'start bin\windows\kafka-server-start.bat config\%s'

print('Starting ZooKeeper Server...')
subprocess.Popen(startZooKeeperServerCmd, shell=True)
time.sleep(10)

zooKeeperPortNumber = 2181
kafkaPortNumber = 9092
kafkaPortNumber2 = 9093
kafkaPortNumber3 = 9094

def polling(portNumber, interval = 5, count = 10):
    while count > 0:
        tmpFile = os.popen('netstat -na','r')
        portNumberStr = str(portNumber)
        print("Polling the port: " + portNumberStr)
        for line in tmpFile.readlines():
            if line.startswith('  TCP    0.0.0.0:' + portNumberStr) or line.startswith('  TCP    127.0.0.1:' + portNumberStr):
                return True
        print("Not yet. " + str(portNumber))
        count -= 1
        time.sleep(interval)
    print("Polling the port: " + portNumberStr + " unsuccessfully.")
    return False

if polling(zooKeeperPortNumber):
    print("Starting the Kafka cluster...")
    subprocess.Popen(startKafkaServerCmd % 'server.properties', shell=True)

    if polling(kafkaPortNumber):
        subprocess.Popen(startKafkaServerCmd % 'server-1.properties', shell=True)

    if polling(kafkaPortNumber2):
        subprocess.Popen(startKafkaServerCmd % 'server-2.properties', shell=True)
else:
    print("Something wrong ...")
    input()#raw_input()

