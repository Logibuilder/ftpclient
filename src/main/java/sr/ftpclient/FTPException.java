package org.example;

import java.io.IOException;

public class FTPException extends IOException {
    private final int code;

    public FTPException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode(){
        return code;
    }
    @Override
    public String toString() {
        return "Erreur FTP " + code + " : " + this.getMessage();
    }


}
