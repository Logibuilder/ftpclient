package org.example;

import java.io.IOException;


public class Main {
    public static void main(String[] args) throws IOException {

        String host = "ftp.ubuntu.com" ;
        int port = 21;

        FTPClient client = new FTPClient();
        client.connect(host, port);
        client.login("anonymous", "yes");

        //client.cd("MPlayer");
        client.ls_l();
        //client.tree();
        //System.out.println(client.treeJSON());
        client.treeJSON();
        client.disconnect();
    }
}