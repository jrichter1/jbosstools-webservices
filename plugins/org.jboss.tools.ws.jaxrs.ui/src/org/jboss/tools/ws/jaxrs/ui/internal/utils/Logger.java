/******************************************************************************* 
 * Copyright (c) 2008 - 2014 Red Hat, Inc. and others. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Xavier Coulon - Initial API and implementation 
 ******************************************************************************/

package org.jboss.tools.ws.jaxrs.ui.internal.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.jboss.tools.ws.jaxrs.ui.JBossJaxrsUIPlugin;

/**
 * A logger wrapper utility for classes in the current bundle only.
 * 
 * @author xcoulon
 * 
 */
public final class Logger {

	/** The debug name, matching the .options file. */
	private static final String DEBUG = JBossJaxrsUIPlugin.PLUGIN_ID + "/debug";

	/** The trace name, matching the .options file. */
	private static final String TRACE = JBossJaxrsUIPlugin.PLUGIN_ID + "/trace";

	private static final ThreadLocal<DateFormat> dateFormatter = new ThreadLocal<DateFormat>() {
		@Override
		protected DateFormat initialValue() {
			return new SimpleDateFormat("HH:mm:ss.SSS");
		}
	};
	
	/**
	 * The private constructor of the static class.
	 */
	private Logger() {
	}

	/**
	 * Logs a message with an 'error' severity.
	 * 
	 * @param message
	 *            the message to log
	 * @param t
	 *            the throwable cause
	 */
	public static void error(String message) {
		JBossJaxrsUIPlugin.getDefault().getLog().log(new Status(Status.ERROR, JBossJaxrsUIPlugin.PLUGIN_ID, message));
	}

	/**
	 * Logs a message with an 'error' severity.
	 * 
	 * @param message
	 *            the message to log
	 * @param t
	 *            the throwable cause
	 */
	public static void error(final String message, final Throwable t) {
		JBossJaxrsUIPlugin.getDefault().getLog()
				.log(new Status(Status.ERROR, JBossJaxrsUIPlugin.PLUGIN_ID, message, t));
	}

	/**
	 * Logs a message with an 'error' severity.
	 * 
	 * @param message
	 *            the message to log
	 * @param t
	 *            the throwable cause
	 */
	public static void error(final String message, final Throwable t, final Object... items) {
		JBossJaxrsUIPlugin.getDefault().getLog()
		.log(new Status(Status.ERROR, JBossJaxrsUIPlugin.PLUGIN_ID, getMessage(message, items), t));
	}

	/**
	 * Logs a message with a 'warning' severity.
	 * 
	 * @param message
	 *            the message to log
	 */
	public static void warn(final String message) {
		JBossJaxrsUIPlugin.getDefault().getLog().log(new Status(Status.WARNING, JBossJaxrsUIPlugin.PLUGIN_ID, message));
	}

	public static void warn(String message, Throwable cause) {
		JBossJaxrsUIPlugin.getDefault().getLog()
				.log(new Status(Status.WARNING, JBossJaxrsUIPlugin.PLUGIN_ID, message, cause));
	}

	public static void info(String message) {
		JBossJaxrsUIPlugin.getDefault().getLog().log(new Status(Status.INFO, JBossJaxrsUIPlugin.PLUGIN_ID, message));
	}

	/**
	 * Outputs a debug message in the trace file (not the error view of the
	 * runtime workbench). Traces must be activated for this plugin in order to
	 * see the output messages.
	 * 
	 * @param message
	 *            the message to trace.
	 */
	public static void debug(final String message) {
		debug(message, (Object[])null);

	}

	/**
	 * Outputs a 'debug' level message in the .log file (not the error view of
	 * the runtime workbench). Traces must be activated for this plugin in order
	 * to see the output messages.
	 * 
	 * @param message
	 *            the message to trace.
	 */
	public static void debug(final String message, Object... items) {
		log(DEBUG, message, items);
	}

	/**
	 * Outputs a 'trace' level message in the .log file (not the error view of
	 * the runtime workbench). Traces must be activated for this plugin in order
	 * to see the output messages.
	 * 
	 * @param message
	 *            the message to trace.
	 */
	public static void trace(final String message, final Object... items) {
		log(TRACE, message, items);
	}

	private static void log(final String level, final String message, final Object... items) {
		try {
			final String debugOption = Platform.getDebugOption(level);
			if (JBossJaxrsUIPlugin.getDefault() != null && JBossJaxrsUIPlugin.getDefault().isDebugging()
					&& "true".equalsIgnoreCase(debugOption)) {
				final String valuedMessage = getMessage(message, items);
				System.out.println(dateFormatter.get().format(new Date()) + " [" + Thread.currentThread().getName()
						+ "] " + toLevel(level) + " " + valuedMessage);
			}
		} catch (RuntimeException e) {
			System.err.println("Failed to write proper debug message with template:\n " + message + "\n and items:");
			for (Object item : items) {
				System.err.println(" " + item);
			}
		}
	}

	private static String toLevel(final String level) {
		if(level.equals(DEBUG)) {
			return "DEBUG";
		}
		if(level.equals(TRACE)) {
			return "TRACE";
		}
		return "UNKNOWN_LEVEL";
	}

	/**
	 * @param valuedMessage
	 * @param items
	 * @return
	 */
	public static String getMessage(String valuedMessage, final Object... items) {
		if (items != null) {
			for (Object item : items) {
				valuedMessage = valuedMessage.replaceFirst("\\{\\}", (item != null ? item.toString()
						.replaceAll("\\$", ".") : "null"));
			}
		}
		return valuedMessage;
	}

}
