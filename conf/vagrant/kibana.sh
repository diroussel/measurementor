#!/usr/bin/env bash

mkdir /opt/kibana
cd /home/vagrant
sudo wget https://download.elastic.co/kibana/kibana/kibana-4.1.1-linux-x64.tar.gz
tar -zxvf kibana-4.1.1-linux-x64.tar.gz
sudo rm kibana-4.1.1-linux-x64.tar.gz
cp /home/vagrant/kibana-*/* /opt/kibana/ -R

cd /etc/init.d && sudo wget https://gist.githubusercontent.com/thisismitch/8b15ac909aed214ad04a/raw/bce61d85643c2dcdfbc2728c55a41dab444dca20/kibana4
chmod +x /etc/init.d/kibana4
update-rc.d kibana4 defaults 96 9
service kibana4 start