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

package com.ericdaugherty.mail.server.services.smtp;

//Java imports
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ericdaugherty.mail.server.configuration.ConfigurationManager;
import com.ericdaugherty.mail.server.server.errors.NotFoundException;
import com.ericdaugherty.mail.server.server.info.EmailAddress;
import com.ericdaugherty.mail.server.server.info.User;
import com.ericdaugherty.mail.server.server.services.general.DeliveryService;


/**
 * This class (thread) is responsible for checking the disk for unsent message
 * and delivering them to the proper local address or remote smtp server.
 * <p>
 * There should be only one instance of this thread running in the system at any
 * one time.
 */
public class SMTPSender implements Runnable {

	// ***************************************************************
	// Variables
	// ***************************************************************

	/** Logger */
	private static Logger log = LoggerFactory.getLogger(SMTPSender.class);

	/** The ConfigurationManager */
	private static ConfigurationManager configurationManager = ConfigurationManager
			.getInstance();

	private boolean running = true;

	// ***************************************************************
	// Public Interface
	// ***************************************************************

	/**
	 * The entrypoint for this thread. This method handles the lifecycle of this
	 * thread.
	 */
	public void run() {

		while (running) {

			try {

				log.debug("Checking for SMTP messages to deliver");

				File smtpDirectory = new File(
						configurationManager.getMailDirectory()
								+ File.separator + "smtp");

				if (smtpDirectory.exists() && smtpDirectory.isDirectory()) {

					File[] files = smtpDirectory.listFiles();
					int numFiles = files.length;

					for (int index = 0; index < numFiles; index++) {
						try {
							deliver(SMTPMessage.load(files[index]
									.getAbsolutePath()));
						} catch (Throwable throwable) {
							log.error(
									"An error occured attempting to deliver an SMTP Message: "
											+ throwable, throwable);
							// Do nothing else, contine on to the next message.
						}
					}
				}

				// Rest the specified sleep time. If it is greater than 10
				// seconds
				// Wake up every 10 seconds to check to see if the thread is
				// shutting
				// down.
				long sleepTime = configurationManager
						.getDeliveryIntervealMilliseconds();
				if (configurationManager.getDeliveryIntervealMilliseconds() < 10000) {
					Thread.sleep(sleepTime);
				} else {
					long totalSleepTime = sleepTime;
					while (totalSleepTime > 0 && running) {
						if (totalSleepTime > 10000) {
							totalSleepTime -= 10000;
							Thread.sleep(10000);
						} else {
							Thread.sleep(totalSleepTime);
							totalSleepTime = 0;
						}
					}
				}
			} catch (InterruptedException ie) {
				log.error("Sleeping Thread was interrupted.");
			} catch (Throwable throwable) {
				log.error(
						"An error occured attempting to deliver an SMTP Message: "
								+ throwable, throwable);
			}
		}
		log.warn("SMTPSender shut down gracefully.");
	}

	/**
	 * Notifies this thread to stop processing and exit.
	 */
	public void shutdown() {
		log.warn("Attempting to shut down SMTPSender.");
		running = false;
	}

	// ***************************************************************
	// Private Interface
	// ***************************************************************

	/**
	 * This method takes a SMTPMessage and attempts to deliver it. This method
	 * assumes that all the addresses have been validated before, and does not
	 * perform any delivery rules.
	 */
	private void deliver(SMTPMessage message) {

		List toAddresses = message.getToAddresses();
		int numAddress = toAddresses.size();
		Vector failedAddress = new Vector();
		EmailAddress address = null;

		// If the next scheduled delivery attempt is still in the future, skip.
		if (message.getScheduledDelivery().getTime() > System
				.currentTimeMillis()) {
			if (log.isDebugEnabled())
				log.debug("Skipping delivery of message "
						+ message.getMessageLocation().getName()
						+ " because the scheduled delivery time is still in the future: "
						+ message.getScheduledDelivery());
			return;
		}

		for (int index = 0; index < numAddress; index++) {
			try {
				address = (EmailAddress) toAddresses.get(index);
				if (log.isDebugEnabled()) {
					log.debug("Attempting to deliver message from: "
							+ message.getFromAddress().getAddress() + " to: "
							+ address);
				}

				DeliveryService deliveryService = DeliveryService
						.getDeliveryService();
				deliverLocalMessage(address, message);

				if (log.isInfoEnabled()) {
					log.info("Delivery complete for message "
							+ message.getMessageLocation().getName() + " to: "
							+ address);
				}
			} catch (Throwable throwable) {
				log.error("Delivery failed for message from: "
						+ message.getFromAddress().getAddress() + " to: "
						+ address + " - " + throwable, throwable);
				failedAddress.addElement(toAddresses.get(index));
			}
		}

		// If all addresses were successful, remove the message from the spool
		if (failedAddress.size() == 0) {
			// Log an error if the delete fails. This will cause the message to
			// get
			// delivered again, but it is too late to roll back the delivery.
			if (!message.getMessageLocation().delete()) {
				log.error("Error removed SMTP message after delivery!  This message may be redelivered. "
						+ message.getMessageLocation().getName());
			}
		}
	}

	/**
	 * This method takes a local SMTPMessage and attempts to deliver it.
	 */
	private void deliverLocalMessage(EmailAddress address, SMTPMessage message)
			throws NotFoundException {

		if (log.isDebugEnabled()) {
			log.debug("Delivering Message to local user: "
					+ address.getAddress());
		}

		// Load the user. If the user doesn't exist, a not found exception will
		// be thrown and the deliver() message will deal with the notification.
		User user = configurationManager.getUser();

		// The file to write to.
		File messageFile = null;
		// The output stream to write the message to.
		BufferedWriter out = null;

		try {

			// Get the directory and create a new file.
			File userDirectory = user.getUserDirectory();
			messageFile = userDirectory.createTempFile("pop", ".jmsg",
					userDirectory);

			if (log.isDebugEnabled()) {
				log.debug("Delivering to: " + messageFile.getAbsolutePath());
			}

			// Open the output stream.
			out = new BufferedWriter(new FileWriter(messageFile));

			// Get the data to write.
			List dataLines = message.getDataLines();
			int numDataLines = dataLines.size();

			// Write the X-DeliveredTo: header
			out.write("X-DeliveredTo: " + address.getAddress());
			out.write("\r\n");

			// Write the data.
			for (int index = 0; index < numDataLines; index++) {
				out.write((String) dataLines.get(index));
				out.write("\r\n");
			}
		} catch (IOException ioe) {
			log.error("Error performing local delivery.", ioe);
			if (messageFile != null) {
				// The message was not fully written, so delete it.
				messageFile.delete();
			}
		} finally {
			if (out != null) {
				try {
					// Make sure we close up the output stream.
					out.close();
				} catch (IOException ioe) {
					log.error("Error closing output Stream.", ioe);
				}
			}
		}
	}
}
// EOF