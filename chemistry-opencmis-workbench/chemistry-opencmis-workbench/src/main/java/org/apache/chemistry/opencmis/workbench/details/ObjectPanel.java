/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.chemistry.opencmis.workbench.details;

import groovy.ui.Console;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.FileableCmisObject;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.bindings.spi.atompub.LinkAccess;
import org.apache.chemistry.opencmis.workbench.ClientHelper;
import org.apache.chemistry.opencmis.workbench.model.ClientModel;
import org.apache.chemistry.opencmis.workbench.model.ClientModelEvent;
import org.apache.chemistry.opencmis.workbench.model.ObjectListener;
import org.apache.chemistry.opencmis.workbench.swing.InfoPanel;

public class ObjectPanel extends InfoPanel implements ObjectListener {

    private static final long serialVersionUID = 1L;

    private JTextField nameField;
    private JTextField idField;
    private JTextField typeField;
    private JTextField basetypeField;
    private JTextField versionLabelField;
    private JTextField pwcField;
    private JTextField contentUrlField;
    private InfoList paths;
    private InfoList allowableActionsList;
    private JButton refreshButton;
    private JPanel groovyPanel;
    private JButton groovyOpenButton;
    private JButton groovyRunButton;
    private JTextArea groovyOutput;
    private JTextAreaWriter groovyOutputWriter;

    public ObjectPanel(ClientModel model) {
        super(model);

        model.addObjectListener(this);

        createGUI();
    }

    public void objectLoaded(ClientModelEvent event) {
        CmisObject object = getClientModel().getCurrentObject();

        if (object == null) {
            nameField.setText("");
            idField.setText("");
            typeField.setText("");
            basetypeField.setText("");
            versionLabelField.setText("");
            pwcField.setText("");
            paths.removeAll();
            contentUrlField.setText("");
            allowableActionsList.removeAll();
            refreshButton.setEnabled(false);
            groovyPanel.setVisible(false);
        } else {
            try {
                nameField.setText(object.getName());
                idField.setText(object.getId());
                typeField.setText(object.getType().getId());
                basetypeField.setText(object.getBaseTypeId().toString());
                if (object instanceof Document) {
                    Document doc = (Document) object;

                    try {
                        versionLabelField.setText(doc.getVersionLabel());
                    } catch (Exception e) {
                        versionLabelField.setText("???");
                    }

                    if (doc.isVersionSeriesCheckedOut() == null) {
                        pwcField.setText("");
                    } else if (doc.isVersionSeriesCheckedOut().booleanValue()) {
                        pwcField.setText(doc.getVersionSeriesCheckedOutId());
                    } else {
                        pwcField.setText("(not checked out)");
                    }
                } else {
                    pwcField.setText("");
                    versionLabelField.setText("");
                }

                if (object instanceof FileableCmisObject) {
                    if (object instanceof Folder) {
                        paths.setList(Collections.singletonList(((Folder) object).getPath()));
                    } else {
                        paths.setList(Collections.singletonList(""));
                        final FileableCmisObject pathObject = (FileableCmisObject) object;
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    List<String> pathsList = pathObject.getPaths();
                                    if ((pathsList == null) || (pathsList.size() == 0)) {
                                        paths.setList(Collections.singletonList("(unfiled)"));
                                    } else {
                                        paths.setList(pathsList);
                                    }
                                } catch (Exception e) {
                                    paths.setList(Collections.singletonList("(???)"));
                                    // ClientHelper.showError(null, e);
                                }
                                ObjectPanel.this.revalidate();
                            }
                        });
                    }
                } else {
                    paths.setList(Collections.singletonList("(not filable)"));
                }

                String docUrl = getDocumentURL(object, getClientModel().getClientSession().getSession());
                if (docUrl != null) {
                    contentUrlField.setText(docUrl);
                } else {
                    contentUrlField.setText("(not available)");
                }

                if (object.getAllowableActions() != null) {
                    allowableActionsList.setList(object.getAllowableActions().getAllowableActions());
                } else {
                    allowableActionsList.setList(Collections.singletonList("(missing)"));
                }

                refreshButton.setEnabled(true);

                if ((object instanceof Document) && (object.getName().toLowerCase().endsWith(".groovy"))) {
                    groovyPanel.setVisible(true);
                    groovyOutput.setVisible(false);
                } else {
                    groovyPanel.setVisible(false);
                }
            } catch (Exception e) {
                ClientHelper.showError(this, e);
            }
        }

        revalidate();
    }

    private void createGUI() {
        setupGUI();

        nameField = addLine("Name:", true);
        idField = addId("Id:");
        typeField = addLine("Type:");
        basetypeField = addLine("Base Type:");
        paths = addComponent("Paths:", new InfoList());
        versionLabelField = addLine("Version Label:");
        pwcField = addId("PWC:");
        contentUrlField = addLink("Content URL:");
        allowableActionsList = addComponent("Allowable Actions:", new InfoList());
        refreshButton = addComponent("", new JButton("Refresh"));
        refreshButton.setEnabled(false);

        groovyPanel = addComponent("", new JPanel(new BorderLayout()));
        groovyPanel.setOpaque(false);
        groovyPanel.setVisible(false);

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.LINE_AXIS));
        buttonPanel.setOpaque(false);
        groovyPanel.add(buttonPanel, BorderLayout.PAGE_START);
        groovyOpenButton = new JButton("Open Script");
        buttonPanel.add(groovyOpenButton);
        groovyRunButton = new JButton("Run Script");
        buttonPanel.add(groovyRunButton);

        groovyOutput = new JTextArea(null, 1, 80);
        groovyOutput.setEditable(false);
        groovyOutput.setFont(Font.decode("Monospaced"));
        groovyOutput.setBorder(BorderFactory.createTitledBorder(""));
        groovyOutputWriter = new JTextAreaWriter(groovyOutput);
        groovyPanel.add(groovyOutput, BorderLayout.CENTER);

        refreshButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    getClientModel().reloadObject();
                } catch (Exception ex) {
                    ClientHelper.showError(null, ex);
                } finally {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        groovyOpenButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    Document doc = (Document) getClientModel().getCurrentObject();
                    File file = ClientHelper.createTempFileFromDocument(doc, null);
                    Console console = ClientHelper.openConsole(ObjectPanel.this, getClientModel(), null);
                    if (console != null) {
                        console.loadScriptFile(file);
                    }
                } catch (Exception ex) {
                    ClientHelper.showError(null, ex);
                } finally {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

        groovyRunButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                try {
                    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
                    Document doc = (Document) getClientModel().getCurrentObject();
                    File file = ClientHelper.createTempFileFromDocument(doc, null);
                    groovyOutput.setText("");
                    groovyOutput.setVisible(true);
                    groovyOutput.invalidate();
                    ClientHelper.runGroovyScript(ObjectPanel.this, getClientModel(), file, groovyOutputWriter);
                } catch (Exception ex) {
                    ClientHelper.showError(null, ex);
                } finally {
                    setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
            }
        });

    }

    public String getDocumentURL(final CmisObject document, final Session session) {
        if (session.getBinding().getObjectService() instanceof LinkAccess) {
            return ((LinkAccess) session.getBinding().getObjectService()).loadContentLink(session.getRepositoryInfo()
                    .getId(), document.getId());
        }

        return null;
    }

    private static class JTextAreaWriter extends Writer {
        private final JTextArea textArea;

        public JTextAreaWriter(JTextArea textArea) {
            this.textArea = textArea;
        }

        @Override
        public void write(final char[] cbuf, final int off, final int len) throws IOException {
            final String s = new String(cbuf, off, len);
            SwingUtilities.invokeLater(new Runnable() {
                public void run() {
                    textArea.append(s);
                }
            });
        }

        @Override
        public void flush() throws IOException {
        }

        @Override
        public void close() throws IOException {
        }
    }
}