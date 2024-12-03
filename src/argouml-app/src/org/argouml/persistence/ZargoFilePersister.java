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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
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
            LOG.log(Level.SEVERE, "Exception occurred during save attempt", e);
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (Exception ex) {
                // Do nothing.
            }

            if (doSafeSaves) {
                // In case of exception, rollback to old file
                file.delete();
                if (tempFile != null && tempFile.exists()) {
                    tempFile.renameTo(file);
                }
            }
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
    @Override
    public Project doLoad(File file)
            throws OpenException, InterruptedException {

        ProgressMgr progressMgr = new ProgressMgr();
        progressMgr.setNumberOfPhases(3 + UML_PHASES_LOAD);
        ThreadUtils.checkIfInterrupted();

        int fileVersion;
        String releaseVersion;

        try (ZipFile zipFile = new ZipFile(file)) {
            String argoEntry = getEntryNames(zipFile, ".argo").iterator().next();

            if (!isValidEntryName(argoEntry)) {
                throw new OpenException("Invalid or unsafe .argo entry name");
            }

            ZipEntry argoZipEntry = zipFile.getEntry(argoEntry);

            if (argoZipEntry == null || argoZipEntry.isDirectory()) {
                throw new OpenException(".argo entry not found or is a directory");
            }

            try (InputStream argoStream = zipFile.getInputStream(argoZipEntry)) {
                fileVersion = getPersistenceVersion(argoStream);
            }

            try (InputStream argoStream = zipFile.getInputStream(argoZipEntry)) {
                releaseVersion = getReleaseVersion(argoStream);
            }

            boolean upgradeRequired = true;

            // Disable upgrade for UML2 projects
            if (Model.getFacade().getUmlVersion().charAt(0) == '2') {
                upgradeRequired = false;
            }

            LOG.log(Level.INFO, "Loading zargo file of version {0}", fileVersion);

            final Project p;
            if (upgradeRequired) {
                File combinedFile = zargoToUml(file, progressMgr);
                p = super.doLoad(file, combinedFile, progressMgr);
            } else {
                p = loadFromZargo(file, zipFile, progressMgr);
            }

            progressMgr.nextPhase();

            PersistenceManager.getInstance().setProjectURI(file.toURI(), p);
            return p;

        } catch (IOException e) {
            throw new OpenException(e);
        }
    }

    // Utility to validate entry name (only alphanumeric and limited special chars allowed)
    private static boolean isValidEntryName(String entryName) {
        if (entryName == null || entryName.isEmpty()) {
            return false;
        }
        // Disallow path traversal sequences
        if (entryName.contains("..") || entryName.contains("/") || entryName.contains("\\")) {
            return false;
        }
        String allowedPattern = "^[a-zA-Z0-9_.-]+$"; // Allow only alphanumeric, underscores, dots, and dashes
        return Pattern.matches(allowedPattern, entryName);
    }

    private Project loadFromZargo(File file, ZipFile zipFile, ProgressMgr progressMgr)
            throws OpenException {

        Project p = ProjectFactory.getInstance().createProject(file.toURI());
        try {
            progressMgr.nextPhase();

            // Load .argo project descriptor
            ArgoParser parser = new ArgoParser();

            String argoEntry = getEntryNames(zipFile, ".argo").iterator().next();
            if (!isValidEntryName(argoEntry)) {
                throw new OpenException("Invalid or unsafe .argo entry name");
            }

            ZipEntry argoZipEntry = zipFile.getEntry(argoEntry);

            if (argoZipEntry == null || argoZipEntry.isDirectory()) {
                throw new OpenException(".argo entry not found or is a directory");
            }

            parser.readProject(p, new InputSource(zipFile.getInputStream(argoZipEntry)));

            List memberList = parser.getMemberList();

            LOG.log(Level.INFO, memberList.size() + " members");

            // Load .xmi file before any PGML files
            String xmiEntry = getEntryNames(zipFile, ".xmi").iterator().next();
            MemberFilePersister persister = getMemberFilePersister("xmi");

            ZipEntry xmiZipEntry = zipFile.getEntry(xmiEntry);
            if (xmiZipEntry == null || xmiZipEntry.isDirectory()) {
                throw new OpenException(".xmi entry not found or is a directory");
            }

            persister.load(p, new InputSource(zipFile.getInputStream(xmiZipEntry)));

            // Load the rest
            List<String> entries = getEntryNames(zipFile, null);
            for (String name : entries) {
                String ext = name.substring(name.lastIndexOf('.') + 1);
                if (!"argo".equals(ext) && !"xmi".equals(ext)) {
                    if (!isValidEntryName(name)) {
                        continue;
                    }
                    persister = getMemberFilePersister(ext);

                    LOG.log(Level.INFO,
                            "Loading member with "
                                    + persister.getClass().getName());

                    ZipEntry entry = zipFile.getEntry(name);
                    if (entry == null || entry.isDirectory()) {
                        continue;
                    }

                    persister.load(p, new InputSource(zipFile.getInputStream(entry)));
                }
            }

            progressMgr.nextPhase();
            ThreadUtils.checkIfInterrupted();
            p.postLoad();
            return p;
        } catch (InterruptedException e) {
            return null;
        } catch (IOException e) {
            throw new OpenException(e);
        } catch (SAXException e) {
            throw new OpenException(e);
        }
    }

    private File zargoToUml(File file, ProgressMgr progressMgr)
            throws OpenException, InterruptedException {

        File combinedFile = null;
        try (ZipFile zipFile = new ZipFile(file)) {
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

            copyArgo(zipFile, encoding, writer);

            progressMgr.nextPhase();

            copyMember(zipFile, "profile", encoding, writer);

            copyXmi(zipFile, encoding, writer);

            copyDiagrams(zipFile, encoding, writer);

            // Copy the todo items after the model and diagrams so that
            // any model elements or figs that the todo items refer to
            // will exist before creating critics.
            copyMember(zipFile, "todo", encoding, writer);

            progressMgr.nextPhase();

            writer.println("</uml>");
            writer.close();
            LOG.log(Level.INFO, "Completed combining files");
        } catch (IOException e) {
            throw new OpenException(e);
        }
        return combinedFile;
    }

    private void copyArgo(ZipFile zipFile, String encoding, PrintWriter writer)
            throws IOException, OpenException,
            UnsupportedEncodingException {

        int pgmlCount = getPgmlCount(zipFile);
        boolean containsToDo = containsTodo(zipFile);
        boolean containsProfile = containsProfile(zipFile);

        // Read the .argo file from the ZIP
        String argoEntryName = getEntryNames(zipFile, FileConstants.PROJECT_FILE_EXT).iterator().next();

        if (argoEntryName == null) {
            throw new OpenException("There is no .argo file in the .zargo");
        }

        ZipEntry argoEntry = zipFile.getEntry(argoEntryName);
        if (argoEntry == null || argoEntry.isDirectory()) {
            throw new OpenException("Can't find .argo entry in the zip file");
        }

        String line;
        BufferedReader reader =
                new BufferedReader(new InputStreamReader(zipFile.getInputStream(argoEntry), encoding));
        // Keep reading till we hit the <argo> tag
        String rootLine;
        do {
            rootLine = reader.readLine();
            if (rootLine == null) {
                throw new OpenException(
                        "Can't find an <argo> tag in the argo file");
            }
        } while (!rootLine.startsWith("<argo"));

        // Get the version from the tag.
        String version = getVersion(rootLine);
        writer.println("<uml version=\"" + version + "\">");
        writer.println(rootLine);
        LOG.log(Level.INFO, "Transferring argo contents");
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

        reader.close();
    }

    private void copyXmi(ZipFile zipFile, String encoding, PrintWriter writer)
            throws IOException, UnsupportedEncodingException {

        String xmiEntryName = getEntryNames(zipFile, ".xmi").iterator().next();
        ZipEntry xmiEntry = zipFile.getEntry(xmiEntryName);
        if (xmiEntry == null || xmiEntry.isDirectory()) {
            throw new IOException(".xmi entry not found or is a directory");
        }
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(zipFile.getInputStream(xmiEntry), encoding));
        // Skip 1 line
        reader.readLine();

        readerToWriter(reader, writer);

        reader.close();
    }

    private void copyDiagrams(ZipFile zipFile, String encoding, PrintWriter writer)
            throws IOException {

        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry currentEntry = entries.nextElement();
            if (currentEntry.getName().endsWith(".pgml") && !currentEntry.isDirectory()) {
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(zipFile.getInputStream(currentEntry), encoding));
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
                reader.close();
            }
        }
    }

    private void copyMember(ZipFile zipFile, String tag, String outputEncoding,
                            PrintWriter writer) throws IOException,
            UnsupportedEncodingException {

        String entryName = getEntryNames(zipFile, "." + tag).stream().findFirst().orElse(null);

        if (entryName != null) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry != null && !entry.isDirectory()) {
                InputStreamReader isr = new InputStreamReader(zipFile.getInputStream(entry), outputEncoding);
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

                reader.close();
            }
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

    private int getPgmlCount(ZipFile zipFile) throws IOException {
        return getEntryNames(zipFile, ".pgml").size();
    }

    private boolean containsTodo(ZipFile zipFile) throws IOException {
        return !getEntryNames(zipFile, ".todo").isEmpty();
    }

    private boolean containsProfile(ZipFile zipFile) throws IOException {
        return !getEntryNames(zipFile, "." + ProfileConfiguration.EXTENSION)
                .isEmpty();
    }

    /**
     * Get a list of zip file entries which end with the given extension.
     * If the extension is null, all entries are returned.
     */
    private List<String> getEntryNames(ZipFile zipFile, String extension)
            throws IOException {

        List<String> result = new ArrayList<String>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            String name = entry.getName();
            if (extension == null || name.endsWith(extension)) {
                if (isValidEntryName(name)) {
                    result.add(name);
                }
            }
        }
        return result;
    }
}
