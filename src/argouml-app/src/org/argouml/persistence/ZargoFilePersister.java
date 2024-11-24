/* $Id$
 *****************************************************************************
 * Copyright (c) 2009-2012 Contributors - see below
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    tfmorris
 *    Michiel van der Wulp
 *****************************************************************************
 *
 * Some portions of this file was previously release using the BSD License:
 */

// Copyright (c) 1996-2008 The Regents of the University of California. All
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

package org.argouml.persistence;

import java.io.*;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.argouml.application.api.Argo;
import org.argouml.application.helpers.ApplicationVersion;
import org.argouml.i18n.Translator;
import org.argouml.kernel.ProfileConfiguration;
import org.argouml.kernel.Project;
import org.argouml.kernel.ProjectFactory;
import org.argouml.kernel.ProjectMember;
import org.argouml.model.Model;
import org.argouml.util.FileConstants;
import org.argouml.util.ThreadUtils;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * To persist to and from zargo (zipped file) storage.
 *
 * @author Bob Tarling
 */
class ZargoFilePersister extends UmlFilePersister {
    /**
     * Logger.
     */
    private static final Logger LOG =
        Logger.getLogger(ZargoFilePersister.class.getName());

    /**
     * The constructor.
     */
    public ZargoFilePersister() {
    }

    /*
     * @see org.argouml.persistence.AbstractFilePersister#getExtension()
     */
    @Override
    public String getExtension() {
        return "zargo";
    }

    /*
     * @see org.argouml.persistence.AbstractFilePersister#getDesc()
     */
    @Override
    protected String getDesc() {
        return Translator.localize("combobox.filefilter.zargo");
    }

    /**
     * It is being considered to save out individual xmi's from individuals
     * diagrams to make it easier to modularize the output of Argo.
     *
     * @param file
     *            The file to write.
     * @param project
     *            the project to save
     * @throws SaveException
     *             when anything goes wrong
     * @throws InterruptedException     if the thread is interrupted
     *
     * @see org.argouml.persistence.ProjectFilePersister#save(
     *      org.argouml.kernel.Project, java.io.File)
     */
    @Override
    public void doSave(Project project, File file) throws SaveException,
    InterruptedException {

        LOG.log(Level.INFO, "Saving");
        /* Retain the previous project file even when the save operation
         * crashes in the middle. Also create a backup file after saving. */
        boolean doSafeSaves = useSafeSaves();

        ProgressMgr progressMgr = new ProgressMgr();
        progressMgr.setNumberOfPhases(4);
        progressMgr.nextPhase();

        File lastArchiveFile = new File(file.getAbsolutePath() + "~");
        File tempFile = null;

        if (doSafeSaves) {
            try {
                tempFile = createTempFile(file);
            } catch (FileNotFoundException e) {
                throw new SaveException(Translator.localize(
                        "optionpane.save-project-exception-cause1"), e);
            } catch (IOException e) {
                throw new SaveException(Translator.localize(
                        "optionpane.save-project-exception-cause2"), e);
            }
        }

        ZipOutputStream stream = null;
        try {

            project.setFile(file);
            project.setVersion(ApplicationVersion.getVersion());
            project.setPersistenceVersion(PERSISTENCE_VERSION);

            stream = new ZipOutputStream(new FileOutputStream(file));

            for (ProjectMember projectMember : project.getMembers()) {
                if (projectMember.getType().equalsIgnoreCase("xmi")) {

                    LOG.log(Level.INFO,
                            "Saving member of type: {0}",
                            projectMember.getType());

                    stream.putNextEntry(
                            new ZipEntry(projectMember.getZipName()));
                    MemberFilePersister persister =
                        getMemberFilePersister(projectMember);
                    persister.save(projectMember, stream);
                }
            }

            if (doSafeSaves) {
                // if save did not raise an exception
                // and name+"#" exists move name+"#" to name+"~"
                // this is the correct backup file
                if (lastArchiveFile.exists()) {
                    lastArchiveFile.delete();
                }
                if (tempFile.exists() && !lastArchiveFile.exists()) {
                    tempFile.renameTo(lastArchiveFile);
                }
                if (tempFile.exists()) {
                    tempFile.delete();
                }
            }

            progressMgr.nextPhase();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Exception occured during save attempt", e);
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception ex) {
                // Do nothing.
            }

            if (doSafeSaves) {
                // frank: in case of exception
                // delete name and mv name+"#" back to name if name+"#" exists
                // this is the "rollback" to old file
                file.delete();
                tempFile.renameTo(file);
            }
            // we have to give a message to user and set the system to unsaved!
            throw new SaveException(e);
        }

        try {
            stream.close();
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Failed to close save output writer", ex);
        }
    }

    /*
     * @see org.argouml.persistence.AbstractFilePersister#isSaveEnabled()
     */
    @Override
    public boolean isSaveEnabled() {
        return false;
    }

    /*
     * @see org.argouml.persistence.ProjectFilePersister#doLoad(java.io.File)
     */


    private Project loadFromZargo(File file, ProgressMgr progressMgr)
        throws OpenException {

        Project p = ProjectFactory.getInstance().createProject(file.toURI());
        try {
            progressMgr.nextPhase();

            // Load .argo project descriptor
            ArgoParser parser = new ArgoParser();
            String argoEntry = getEntryNames(file, ".argo").iterator().next();
            parser.readProject(p, new InputSource(makeZipEntryUrl(toURL(file),
                    argoEntry).toExternalForm()));

            List memberList = parser.getMemberList();

            LOG.log(Level.INFO,memberList.size() + " members");

            // Load .xmi file before any PGML files
            // FIXME: the following is loading the model before anything else.
            // Due to the Zargo containing the profiles, currently we have
            // removed this hack in UmlFilePersister and I think it should be
            // removed from here also.
            String xmiEntry = getEntryNames(file, ".xmi").iterator().next();
            MemberFilePersister persister = getMemberFilePersister("xmi");
            URL url = makeZipEntryUrl(toURL(file), xmiEntry);
            persister.load(p, new InputSource(url.toExternalForm()));

            // Load the rest
            List<String> entries = getEntryNames(file, null);
            for (String name : entries) {
                String ext = name.substring(name.lastIndexOf('.') + 1);
                if (!"argo".equals(ext) && !"xmi".equals(ext)) {
                    persister = getMemberFilePersister(ext);

                    LOG.log(Level.INFO,
                            "Loading member with "
                            + persister.getClass().getName());

                    url = makeZipEntryUrl(toURL(file), name);
                    persister.load(p, new InputSource(url.toExternalForm()));
                }
            }

            progressMgr.nextPhase();
            ThreadUtils.checkIfInterrupted();
            p.postLoad();
            return p;
        } catch (InterruptedException e) {
            return null;
        } catch (MalformedURLException e) {
            throw new OpenException(e);
        } catch (IOException e) {
            throw new OpenException(e);
        } catch (SAXException e) {
            throw new OpenException(e);
        }
    }

    private URL toURL(File file) throws MalformedURLException {
        return file.toURI().toURL();
    }


    private File zargoToUml(File file, ProgressMgr progressMgr)
        throws OpenException, InterruptedException {

        File combinedFile = null;
        try {
            combinedFile = File.createTempFile("combinedzargo_", ".uml");
            LOG.log(Level.INFO,
                    "Combining old style zargo sub files "
                    + "into new style uml file {0}",
                    combinedFile.getAbsolutePath());

            combinedFile.deleteOnExit();

            String encoding = Argo.getEncoding();
            FileOutputStream stream = new FileOutputStream(combinedFile);
            PrintWriter writer =
                new PrintWriter(new BufferedWriter(
                        new OutputStreamWriter(stream, encoding)));

            writer.println("<?xml version = \"1.0\" " + "encoding = \""
                    + encoding + "\" ?>");

            copyArgo(file, encoding, writer);

            progressMgr.nextPhase();

            copyMember(file, "profile", encoding, writer);

            copyXmi(file, encoding, writer);

            copyDiagrams(file, encoding, writer);

            // Copy the todo items after the model and diagrams so that
            // any model elements or figs that the todo items refer to
            // will exist before creating critics.
            copyMember(file, "todo", encoding, writer);

            progressMgr.nextPhase();

            writer.println("</uml>");
            writer.close();
            LOG.log(Level.INFO, "Completed combining files");
        } catch (IOException e) {
            throw new OpenException(e);
        }
        return combinedFile;
    }


    private void copyArgo(File file, String encoding, PrintWriter writer)
        throws IOException, MalformedURLException, OpenException,
        UnsupportedEncodingException {

        int pgmlCount = getPgmlCount(file);
        boolean containsToDo = containsTodo(file);
        boolean containsProfile = containsProfile(file);

        // first read the .argo file from Zip
        ZipInputStream zis =
            openZipStreamAt(toURL(file), FileConstants.PROJECT_FILE_EXT);

        if (zis == null) {
            throw new OpenException(
                    "There is no .argo file in the .zargo");
        }

        String line;
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(zis, encoding));
        // Keep reading till we hit the <argo> tag
        String rootLine;
        do {
            rootLine = reader.readLine();
            if (rootLine == null) {
                throw new OpenException(
                        "Can't find an <argo> tag in the argo file");
            }
        } while(!rootLine.startsWith("<argo"));


        // Get the version from the tag.
        String version = getVersion(rootLine);
        writer.println("<uml version=\"" + version + "\">");
        writer.println(rootLine);
        LOG.log(Level.INFO, "Transfering argo contents");
        int memberCount = 0;
        while ((line = reader.readLine()) != null) {
            if (line.trim().startsWith("<member")) {
                ++memberCount;
            }
            if (line.trim().equals("</argo>") && memberCount == 0) {
                LOG.log(Level.INFO, "Inserting member info");
                writer.println("<member type='xmi' name='.xmi' />");
                for (int i = 0; i < pgmlCount; ++i) {
                    writer.println("<member type='pgml' name='.pgml' />");
                }
                if (containsToDo) {
                    writer.println("<member type='todo' name='.todo' />");
                }
                if (containsProfile) {
                    String type = ProfileConfiguration.EXTENSION;
                    writer.println("<member type='" + type + "' name='."
                            + type + "' />");
                }
            }
            writer.println(line);
        }

        LOG.log(Level.INFO, "Member count = {0}", memberCount);

        zis.close();
        reader.close();
    }

    private void copyXmi(File file, String encoding, PrintWriter writer)
        throws IOException, MalformedURLException,
        UnsupportedEncodingException {

        ZipInputStream zis = openZipStreamAt(toURL(file), ".xmi");
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(zis, encoding));
        // Skip 1 lines
        reader.readLine();

        readerToWriter(reader, writer);

        zis.close();
        reader.close();
    }


    private void copyDiagrams(File file, String encoding, PrintWriter writer)
        throws IOException {

        // Loop round loading the diagrams
        ZipInputStream zis = new ZipInputStream(toURL(file).openStream());
        SubInputStream sub = new SubInputStream(zis);

        ZipEntry currentEntry = null;
        while ((currentEntry = sub.getNextEntry()) != null) {
            if (currentEntry.getName().endsWith(".pgml")) {

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(sub, encoding));
                String firstLine = reader.readLine();
                if (firstLine.startsWith("<?xml")) {
                    // Skip the 2 lines
                    //<?xml version="1.0" encoding="UTF-8" ?>
                    //<!DOCTYPE pgml SYSTEM "pgml.dtd">
                    reader.readLine();
                } else {
                    writer.println(firstLine);
                }

                readerToWriter(reader, writer);
                sub.close();
                reader.close();
            }
        }
        zis.close();
    }


    private void copyMember(File file, String tag, String outputEncoding,
            PrintWriter writer) throws IOException, MalformedURLException,
                UnsupportedEncodingException {

        ZipInputStream zis = openZipStreamAt(toURL(file), "." + tag);

        if (zis != null) {
            InputStreamReader isr = new InputStreamReader(zis, outputEncoding);
            BufferedReader reader = new BufferedReader(isr);

            String firstLine = reader.readLine();
            if (firstLine.startsWith("<?xml")) {
                // Skip the 2 lines
                //<?xml version="1.0" encoding="UTF-8" ?>
                //<!DOCTYPE todo SYSTEM "todo.dtd" >
                reader.readLine();
            } else {
                writer.println(firstLine);
            }

            readerToWriter(reader, writer);

            zis.close();
            reader.close();
        }
    }


    private void readerToWriter(
            Reader reader,
            Writer writer) throws IOException {

        int ch;
        while ((ch = reader.read()) != -1) {
            if (ch == 0xFFFF) {
                LOG.log(Level.INFO, "Stripping out 0xFFFF from save file");
            } else if (ch == 8) {
                LOG.log(Level.INFO, "Stripping out 0x8 from save file");
            } else {
                writer.write(ch);
            }
        }
    }

    /**
     * Open a ZipInputStream to the first file found with a given extension.
     *
     * @param url
     *            The URL of the zip file.
     * @param ext
     *            The required extension.
     * @return the zip stream positioned at the required location or null
     * if the requested extension is not found.
     * @throws IOException
     *             if there is a problem opening the file.
     */
    private ZipInputStream openZipStreamAt(URL url, String ext)
        throws IOException {
        ZipInputStream zis = new ZipInputStream(url.openStream());
        ZipEntry entry = zis.getNextEntry();
        while (entry != null && !entry.getName().endsWith(ext)) {
            entry = zis.getNextEntry();
        }
        if (entry == null) {
            zis.close();
            return null;
        }
        return zis;
    }

    private InputStream openZipEntry(URL url, String entryName)
        throws MalformedURLException, IOException {
        return makeZipEntryUrl(url, entryName).openStream();
    }


    /**
     * A stream of input streams for reading the Zipped file.
     */
    private static class SubInputStream extends FilterInputStream {
        private ZipInputStream in;

        /**
         * The constructor.
         *
         * @param z
         *            the zip input stream
         */
        public SubInputStream(ZipInputStream z) {
            super(z);
            in = z;
        }

        /*
         * @see java.io.InputStream#close()
         */
        @Override
        public void close() throws IOException {
            in.closeEntry();
        }

        /**
         * Reads the next ZIP file entry and positions stream at the beginning
         * of the entry data.
         *
         * @return the ZipEntry just read
         * @throws IOException
         *             if an I/O error has occurred
         */
        public ZipEntry getNextEntry() throws IOException {
            return in.getNextEntry();
        }
    }

    private int getPgmlCount(File file) throws IOException {
        return getEntryNames(file, ".pgml").size();
    }

    private boolean containsTodo(File file) throws IOException {
        return !getEntryNames(file, ".todo").isEmpty();
    }

    private boolean containsProfile(File file) throws IOException {
        return !getEntryNames(file, "." + ProfileConfiguration.EXTENSION)
                .isEmpty();
    }

    /**
     * Get a list of zip file entries which end with the given extension.
     * If the extension is null, all entries are returned.
     */

    @Override
    public Project doLoad(File file)
            throws OpenException, InterruptedException {

        ProgressMgr progressMgr = new ProgressMgr();
        progressMgr.setNumberOfPhases(3 + UML_PHASES_LOAD);
        ThreadUtils.checkIfInterrupted();

        int fileVersion;
        String releaseVersion;
        try {
            // Validate the zip file structure
            validateZipFile(file);

            String argoEntry = getEntryNames(file, ".argo").iterator().next();
            // Validate entry name
            validateZipEntryName(argoEntry);

            // Read zip entry contents securely
            String argoContent = getZipEntryContent(file, argoEntry);
            fileVersion = getPersistenceVersion(new ByteArrayInputStream(argoContent.getBytes(StandardCharsets.UTF_8)));
            releaseVersion = getReleaseVersion(new ByteArrayInputStream(argoContent.getBytes(StandardCharsets.UTF_8)));
        } catch (MalformedURLException e) {
            throw new OpenException(e);
        } catch (IOException e) {
            throw new OpenException(e);
        }

        boolean upgradeRequired = true;
        if (Model.getFacade().getUmlVersion().charAt(0) == '2') {
            upgradeRequired = false;
        }

        LOG.log(Level.INFO, "Loading zargo file of version {0}", fileVersion);

        final Project p;
        if (upgradeRequired) {
            File combinedFile = zargoToUml(file, progressMgr);
            p = super.doLoad(file, combinedFile, progressMgr);
        } else {
            p = loadFromZargo(file, progressMgr);
        }

        progressMgr.nextPhase();
        PersistenceManager.getInstance().setProjectURI(file.toURI(), p);
        return p;
    }

    /**
     * Extracts the content of a specific zip entry as a string.
     */
    private String getZipEntryContent(File file, String entryName) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(toURL(file).openStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName)) {
                    // Read entry content into a string
                    return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IOException("Entry " + entryName + " not found in the zip file.");
    }

    private URL makeZipEntryUrl(URL url, String entryName)
            throws MalformedURLException {
        if (!url.getProtocol().equals("file")) {
            throw new MalformedURLException("Only local file URLs are allowed.");
        }
        String entryURL = "jar:" + url + "!/" + entryName;
        return new URL(entryURL);
    }

    private List<String> getEntryNames(File file, String extension)
            throws IOException, MalformedURLException {
        List<String> result = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(toURL(file).openStream())) {
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
                String name = entry.getName();
                try {
                    validateZipEntryName(name);  // Validate entry names
                } catch (OpenException e) {
                    throw new RuntimeException(e);
                }
                if (extension == null || name.endsWith(extension)) {
                    result.add(name);
                }
                entry = zis.getNextEntry();
            }
        }
        return result;
    }

    private void validateZipFile(File file) throws OpenException {
        if (file == null || !file.exists() || !file.isFile()) {
            throw new OpenException("Invalid or non-existent zip file.");
        }
        try (ZipInputStream zis = new ZipInputStream(toURL(file).openStream())) {
            if (zis.getNextEntry() == null) {
                throw new OpenException("Zip file is empty or invalid.");
            }
        } catch (IOException e) {
            throw new OpenException("Failed to validate zip file.", e);
        }
    }

    private void validateZipEntryName(String entryName) throws OpenException {
        if (entryName == null || entryName.contains("..") || entryName.startsWith("/")) {
            throw new OpenException("Invalid zip entry name: " + entryName);
        }
    }

    private void validateUrl(URL url) throws OpenException {
        try {
            // Resolve the host from the URL
            InetAddress address = InetAddress.getByName(url.getHost());

            // Check if the resolved IP address is a local or private IP
            if (address.isLoopbackAddress() || address.isSiteLocalAddress()) {
                return; // Allow loopback and private addresses
            }

            // Optionally, block all external addresses
            throw new OpenException("Access to external URLs is not allowed: " + url);
        } catch (Exception e) {
            throw new OpenException("Failed to validate URL: " + url, e);
        }
    }


}
