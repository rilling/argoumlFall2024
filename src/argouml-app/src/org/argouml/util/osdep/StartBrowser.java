/* $Id$
 *****************************************************************************
 * Copyright (c) 2009-2012 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    andrea_nironi
 *****************************************************************************
 *
 * Some portions of this file was previously release using the BSD License:
 */

// Copyright (c) 2002-2006 The Regents of the University of California. All
// Rights Reserved. Permission to use, copy, modify, and distribute this
// software and its documentation without fee, and without a written
// agreement is hereby granted, provided that the above copyright notice
// and this paragraph appear in all copies.  This software program and
// documentation are copyrighted by The Regents of the University of
// California. The software program and documentation are supplied "AS
// IS", without any accompanying services from The Regents. The Regents
// does not warrant that the operation of the program will be
// uninterrupted or error-free. The end-user understands that the program
// was developed for research purposes and is advised not to rely
// exclusively on the program for any reason.  IN NO EVENT SHALL THE
// UNIVERSITY OF CALIFORNIA BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
// SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
// ARISING OUT OF THE USE OF THIS SOFTWARE AND ITS DOCUMENTATION, EVEN IF
// THE UNIVERSITY OF CALIFORNIA HAS BEEN ADVISED OF THE POSSIBILITY OF
// SUCH DAMAGE. THE UNIVERSITY OF CALIFORNIA SPECIFICALLY DISCLAIMS ANY
// WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
// MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE. THE SOFTWARE
// PROVIDED HEREUNDER IS ON AN "AS IS" BASIS, AND THE UNIVERSITY OF
// CALIFORNIA HAS NO OBLIGATIONS TO PROVIDE MAINTENANCE, SUPPORT,
// UPDATES, ENHANCEMENTS, OR MODIFICATIONS.

package org.argouml.util.osdep;

import java.awt.*;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @stereotype utility
 */
public class StartBrowser {
    /** logger */
    private static final Logger LOG =
        Logger.getLogger(StartBrowser.class.getName());

    /**
     * Open an URL in the system's default browser.
     *
     * @param url string containing the given URL
     */
	public static void openUrl(String url) {
		Logger LOG = Logger.getLogger("UrlOpener");

		try {
			// Check if Desktop API is supported
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
				Desktop.getDesktop().browse(new URI(url));
			} else {
				// Fallback for OS-specific handling if Desktop is not supported
				if (OsUtil.isWin32()) {
					ProcessBuilder pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
					pb.start();
				} else if (OsUtil.isMac()) {
					// Use 'open' command on MacOS
					ProcessBuilder pb = new ProcessBuilder("open", url);
					pb.start();
				} else {
					// Fallback to using 'xdg-open' for Linux or other Unix-based systems
					ProcessBuilder pb = new ProcessBuilder("xdg-open", url);
					pb.start();
				}
			}
		} catch (Exception e) {
			LOG.log(Level.SEVERE, "Failed to open URL: " + url, e);
		}
	}

    /**
     * Open an URL in the system's default browser.
     *
     * @param url the URL to open
     */
    public static void openUrl(URL url) {
        openUrl(url.toString());
    }

}
