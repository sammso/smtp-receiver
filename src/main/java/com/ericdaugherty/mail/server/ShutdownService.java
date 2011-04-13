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

package com.ericdaugherty.mail.server;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//Log imports

/**
 * This thread runs when the JVM is shutdown.
 *
 * @author Eric Daugherty
 */
public class ShutdownService implements Runnable {

    /** Logger */
    private Logger log = LoggerFactory.getLogger( this.getClass() );

    /**
     * Runs when the JVM is shutdown.  Stops all threads gracefully.
     */
    public void run() {

        log.warn( "Server is shutting down." );

        try {
            Mail.shutdown();

            log.warn( "Server shutdown complete." );
        }
        catch (Exception e) {
            log.error("Failed to terminate properly", e);
        }
    }
}
