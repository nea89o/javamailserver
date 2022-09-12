ssh -t -R 25:localhost:2500 nea89.moe "firewall-cmd --zone=public --add-port=25/tcp;/bin/bash"
