/******************************************************************************
 * This program is SMTP receiver which collect all mails to same inbox.
 * inbox is readable with pop protocol
 * NO mails are forwared.
 ******************************************************************************
 * Copyright (C) 2001-2011, Eric Daugherty, Sampsa Sohlman
 * All rights reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 ******************************************************************************
 *
 * Refactored 2011 by Sampsa Sohlman for smtp receiver use
 *
 ******************************************************************************
 * For current versions and more information, please visit:
 * 
 * http://www.ericdaugherty.com/java/mail
 *
 * or contact the author at:
 * java@ericdaugherty.com
 *
 ******************************************************************************
 * This program is based on the CSRMail project written by Calvin Smith.
 * http://crsemail.sourceforge.net/
 *****************************************************************************/

/*
 */
package com.ericdaugherty.mail.server.configuration;

/**
 * @author Matthew Lohbihler
 */
public class DefaultSmtpServer {
    private String host;
    private int port;
    private String username;
    private String password;

    public String getHost() {
        return host;
    }
    public void setHost(String host) {
        this.host = host;
    }
    public int getPort() {
        return port;
    }
    public void setPort(int port) {
        this.port = port;
    }
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }

    public String toString() {
        return host +":"+ port;
    }
}
