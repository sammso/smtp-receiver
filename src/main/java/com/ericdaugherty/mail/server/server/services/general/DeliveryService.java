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

package com.ericdaugherty.mail.server.server.services.general;

//Java imports
import java.util.Date;
import java.util.Hashtable;
import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericdaugherty.mail.server.configuration.ConfigurationManager;
import com.ericdaugherty.mail.server.configuration.ConfigurationParameterContants;
import com.ericdaugherty.mail.server.server.info.EmailAddress;



/**
 * Handles the evalution of general mail delivery rules, including SMTP Relaying.
 *
 */
public class DeliveryService implements ConfigurationParameterContants {

    //***************************************************************
    // Variables
    //***************************************************************

    /** Logger Category for this class. */
	private Logger log = LoggerFactory.getLogger( this.getClass() );

    /** Singleton Instance */
    private static DeliveryService instance = null;

    private ConfigurationManager configurationManager;

    /** The IP Addresses that have logged into the POP3 server recently */
    private Hashtable authenticatedIps;

    /** The mailboxes that are currently locked */
    private Hashtable lockedMailboxes;

    //***************************************************************
    // Public Interface
    //***************************************************************

    //***************************************************************
    // Constructor

    /**
     * Load the paramters from the Mail configuration.
     */
    protected DeliveryService() {

        configurationManager = ConfigurationManager.getInstance();

        //Initialize the Hashtable for tracking authenticated ip addresses.
        authenticatedIps = new Hashtable();
        //Initialize the Hashtable for tracking locked mailboxes
        lockedMailboxes = new Hashtable();
    }

    //***************************************************************
    // Public Interface

    /**
     * Accessor for the singleton instance for this class.
     */
    public static synchronized DeliveryService getDeliveryService(){
        if ( instance == null ) {
            instance = new DeliveryService();
        }
        return instance;
    }

    /**
     * This method should be called whenever a client authenticates themselves.
     */
    public void ipAuthenticated( String clientIp ) {
        if( log.isDebugEnabled() ) log.debug( "Adding authenticated IP address: " + clientIp );
        authenticatedIps.put( clientIp, new Date() );
    }

    /**
     * This method locks a mailbox so that two clients can not access the same mailbox
     * at the same time.
     */
    public void lockMailbox( EmailAddress address ) {
        lockedMailboxes.put( address.getAddress(), "" );
    }

    /**
     * Checks to see if a user currently has the specified mailbox locked.
     */
    public boolean isMailboxLocked( EmailAddress address ) {
        if( log.isDebugEnabled() ) log.debug( "Locking Mailbox: " + address.getAddress() );
        return lockedMailboxes.containsKey( address.getAddress() );
    }

    /**
     * Unlocks an mailbox.
     */
    public void unlockMailbox( EmailAddress address ) {
        if( log.isDebugEnabled() ) log.debug( "Unlocking Mailbox: " + address.getAddress() );
        lockedMailboxes.remove( address.getAddress() );
    }
    
    //***************************************************************
    // Private Interface
    //***************************************************************
    
    /**
     * Checks the current state to determine if a user from this
     * IP address has authenticated with the POP3 server with the
     * timeout length.
     */
    private boolean isAuthenticated( String clientIp ) {

        boolean retval = false;
        
        //Do a quick check to see if this ip is even registered
        //in the HashTable.
        if( authenticatedIps.containsKey( clientIp ) ) {
            Date authenticationDate = (Date) authenticatedIps.get( clientIp );
            
            //Calculate the current time and the time that the login will timeout.
            long currentTime = System.currentTimeMillis();
            long timeoutTime = authenticationDate.getTime() + configurationManager.getAuthenticationTimeoutMilliseconds();
            
            //If the timeout time is in the future, the ip is still authenticated.
            if( timeoutTime > currentTime ) {
                retval = true;
            }
            else {
                //If the IP address has timed out, remove it from the hashtable.
                authenticatedIps.remove( clientIp );
            }
        }
        return retval;
    }

    /**
     * Returns true if the client IP address matches an IP address in the
     * approvedAddresses array.
     *
     * @param clientIp The IP address to test.
     * @param approvedAddresses The approved list.
     * @return true if the address is approved.
     */
    private boolean isRelayApproved( String clientIp, String[] approvedAddresses ) {

        String approvedAddress;
        for( int index = 0; index < approvedAddresses.length; index++ ) {
            approvedAddress = approvedAddresses[index];
            // Check for an exact match.
            if( clientIp.equals( approvedAddress ) ) {
                return true;
            }
            // Check for a partial match
            else {
                int wildcardIndex = approvedAddress.indexOf( "*" );
                if( wildcardIndex != -1 ) {
                    boolean isMatch = true;
                    StringTokenizer clientIpTokenizer = new StringTokenizer( clientIp, "." );
                    StringTokenizer approvedAddressTokenizer = new StringTokenizer( approvedAddress,  "." );
                    String clientIpToken;
                    String approvedAddressToken;
                    while( clientIpTokenizer.hasMoreTokens() )
                    {
                        try {
                            clientIpToken = clientIpTokenizer.nextToken().trim();
                            approvedAddressToken = approvedAddressTokenizer.nextToken().trim();
                            if( !clientIpToken.equals( approvedAddressToken) && !approvedAddressToken.equals( "*" ) ) {
                                isMatch = false;
                                break;
                            }
                        }
                        catch (NoSuchElementException noSuchElementException) {
                            log.warn( "Invalid ApprovedAddress found: " + approvedAddress + ".  Skipping." );
                            isMatch = false;
                            break;
                        }
                    }
                    // Return true if you had a match.
                    if (isMatch) return true;
                }
            }
        }
        return false;
    }

    /**
     * Returns true if the client email address matches an email address in the
     * approvedEmailAddresses array.
     *
     * @param clientFromEmail The email address to test.
     * @param approvedEmailAddresses The approved list.
     * @return true if the email address is approved.
     */
    private boolean isRelayApprovedForEmail( EmailAddress clientFromEmail, String[] approvedEmailAddresses ) {

        String approvedEmailAddress;
        for( int index = 0; index < approvedEmailAddresses.length; index++ ) {
            approvedEmailAddress = approvedEmailAddresses[index].trim();

            // Check for an exact match (case insensitive).
            if( clientFromEmail.getAddress().equalsIgnoreCase( approvedEmailAddress ) ) {
                return true;
            }
            else if (approvedEmailAddress.startsWith("@")) {
                // Check for a domain
                String domain=approvedEmailAddress.substring(1);
                if (clientFromEmail.getDomain().endsWith(domain)) {
                    return(true);
                }
            }
        }
        return false;
    }
}
//EOF