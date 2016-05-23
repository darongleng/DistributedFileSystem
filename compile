#!/bin/sh

rm -f *~
rm -f *.class
echo "client compilation"
javac FileClient.java
rmic FileClient
echo "done"

echo "server compilation"
javac FileServer.java
rmic FileServer
echo "done"
