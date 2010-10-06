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
package org.apache.chemistry.opencmis.workbench;

import java.awt.Cursor;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;

import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.Tree;
import org.apache.chemistry.opencmis.commons.definitions.DocumentTypeDefinition;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.definitions.RelationshipTypeDefinition;
import org.apache.chemistry.opencmis.workbench.model.ClientModel;
import org.apache.chemistry.opencmis.workbench.swing.CollectionRenderer;
import org.apache.chemistry.opencmis.workbench.swing.InfoPanel;

public class TypesFrame extends JFrame {

    private static final long serialVersionUID = 1L;

    private static final String WINDOW_TITLE = "CMIS Types";

    private ClientModel model;

    private JTree typesTree;
    private TypeInfoPanel typePanel;
    private PropertyDefinitionTable propertyDefinitionTable;

    public TypesFrame(ClientModel model) {
        super();

        this.model = model;
        createGUI();
        loadData();
    }

    private void createGUI() {
        setTitle(WINDOW_TITLE + " - " + model.getRepositoryName());
        setPreferredSize(new Dimension(1000, 700));
        setMinimumSize(new Dimension(200, 60));

        typesTree = new JTree();
        typesTree.setRootVisible(false);
        typesTree.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);

        typesTree.addTreeSelectionListener(new TreeSelectionListener() {
            public void valueChanged(TreeSelectionEvent e) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) ((JTree) e.getSource())
                        .getLastSelectedPathComponent();

                if (node == null) {
                    return;
                }

                ObjectType type = ((TypeNode) node.getUserObject()).getType();
                typePanel.setType(type);
                propertyDefinitionTable.setType(type);
            }
        });

        typePanel = new TypeInfoPanel();
        propertyDefinitionTable = new PropertyDefinitionTable();

        JSplitPane typeSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(typePanel),
                new JScrollPane(propertyDefinitionTable));
        typeSplitPane.setDividerLocation(300);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(typesTree), typeSplitPane);
        splitPane.setDividerLocation(300);

        add(splitPane);

        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        pack();

        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void loadData() {
        try {
            setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

            DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode();

            List<Tree<ObjectType>> types = model.getTypeDescendants();
            for (Tree<ObjectType> tt : types) {
                addLevel(rootNode, tt);
            }

            DefaultTreeModel treeModel = new DefaultTreeModel(rootNode);
            typesTree.setModel(treeModel);
        } catch (Exception ex) {
            ClientHelper.showError(null, ex);
            return;
        } finally {
            setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }

    private void addLevel(DefaultMutableTreeNode parent, Tree<ObjectType> tree) {
        DefaultMutableTreeNode node = new DefaultMutableTreeNode(new TypeNode(tree.getItem()));
        parent.add(node);

        if (tree.getChildren() != null) {
            for (Tree<ObjectType> tt : tree.getChildren()) {
                addLevel(node, tt);
            }
        }
    }

    static class TypeNode {
        private ObjectType type;

        public TypeNode(ObjectType type) {
            this.type = type;
        }

        public ObjectType getType() {
            return type;
        }

        @Override
        public String toString() {
            return type.getDisplayName() + " (" + type.getId() + ")";
        }
    }

    static class TypeInfoPanel extends InfoPanel {

        private static final long serialVersionUID = 1L;

        private JTextField nameField;
        private JTextField descriptionField;
        private JTextField idField;
        private JTextField localNamespaceField;
        private JTextField localNameField;
        private JTextField queryNameField;
        private JTextField baseTypeField;
        private JCheckBox creatableBox;
        private JCheckBox fileableBox;
        private JCheckBox queryableBox;
        private JCheckBox aclBox;
        private JCheckBox policyBox;
        private JCheckBox versionableBox;
        private JTextField contentStreamAllowedField;
        private JTextField allowedSourceTypesField;
        private JTextField allowedTargetTypesField;

        public TypeInfoPanel() {
            super();
            createGUI();
        }

        public void setType(ObjectType type) {
            if (type != null) {
                nameField.setText(type.getDisplayName());
                descriptionField.setText(type.getDescription());
                idField.setText(type.getId());
                localNamespaceField.setText(type.getLocalNamespace());
                localNameField.setText(type.getLocalName());
                queryNameField.setText(type.getQueryName());
                baseTypeField.setText(type.getBaseTypeId().value());
                creatableBox.setSelected(is(type.isCreatable()));
                fileableBox.setSelected(is(type.isFileable()));
                queryableBox.setSelected(is(type.isQueryable()));
                aclBox.setSelected(is(type.isControllableAcl()));
                policyBox.setSelected(is(type.isControllablePolicy()));

                if (type instanceof DocumentTypeDefinition) {
                    DocumentTypeDefinition docType = (DocumentTypeDefinition) type;
                    versionableBox.setVisible(true);
                    versionableBox.setSelected(is(docType.isVersionable()));
                    contentStreamAllowedField.setVisible(true);
                    contentStreamAllowedField.setText(docType.getContentStreamAllowed() == null ? "???" : docType
                            .getContentStreamAllowed().toString());
                } else {
                    versionableBox.setVisible(false);
                    contentStreamAllowedField.setVisible(false);
                }

                if (type instanceof RelationshipTypeDefinition) {
                    RelationshipTypeDefinition relationshipType = (RelationshipTypeDefinition) type;
                    allowedSourceTypesField.setVisible(true);
                    allowedSourceTypesField.setText(relationshipType.getAllowedSourceTypeIds() == null ? "???"
                            : relationshipType.getAllowedSourceTypeIds().toString());
                    allowedTargetTypesField.setVisible(true);
                    allowedTargetTypesField.setText(relationshipType.getAllowedTargetTypeIds() == null ? "???"
                            : relationshipType.getAllowedTargetTypeIds().toString());
                } else {
                    allowedSourceTypesField.setVisible(false);
                    allowedTargetTypesField.setVisible(false);
                }
            } else {
                nameField.setText("");
                descriptionField.setText("");
                idField.setText("");
                localNamespaceField.setText("");
                localNameField.setText("");
                queryNameField.setText("");
                baseTypeField.setText("");
                creatableBox.setSelected(false);
                fileableBox.setSelected(false);
                queryableBox.setSelected(false);
                aclBox.setSelected(false);
                policyBox.setSelected(false);
                versionableBox.setVisible(false);
                contentStreamAllowedField.setVisible(false);
                allowedSourceTypesField.setVisible(false);
                allowedTargetTypesField.setVisible(false);
            }

            revalidate();
        }

        private void createGUI() {
            setupGUI();

            nameField = addLine("Name:", true);
            descriptionField = addLine("Description:");
            idField = addLine("Id:");
            localNamespaceField = addLine("Local Namespace:");
            localNameField = addLine("Local Name:");
            queryNameField = addLine("Query Name:");
            baseTypeField = addLine("Base Type:");
            creatableBox = addCheckBox("Creatable:");
            fileableBox = addCheckBox("Fileable:");
            queryableBox = addCheckBox("Queryable:");
            aclBox = addCheckBox("ACL controlable:");
            policyBox = addCheckBox("Policy controlable:");
            versionableBox = addCheckBox("Versionable:");
            contentStreamAllowedField = addLine("Content stream allowed:");
            allowedSourceTypesField = addLine("Allowed source types:");
            allowedTargetTypesField = addLine("Allowed target types:");
        }

        private boolean is(Boolean b) {
            if (b == null) {
                return false;
            }

            return b.booleanValue();
        }
    }

    static class PropertyDefinitionTable extends JTable {

        private static final long serialVersionUID = 1L;

        private static final String[] COLUMN_NAMES = { "Name", "Id", "Description", "Local Namespace", "Local Name",
                "Query Name", "Type", "Cardinality", "Updatability", "Queryable", "Required", "Inherited",
                "Default Value", "Choices" };
        private static final int[] COLUMN_WIDTHS = { 200, 200, 200, 200, 200, 200, 80, 80, 80, 50, 50, 50, 200, 200 };

        private ObjectType type;
        private List<PropertyDefinition<?>> propertyDefintions;

        public PropertyDefinitionTable() {
            setDefaultRenderer(Collection.class, new CollectionRenderer());
            setModel(new PropertyDefinitionTableModel(this));

            setAutoResizeMode(AUTO_RESIZE_OFF);

            for (int i = 0; i < COLUMN_WIDTHS.length; i++) {
                TableColumn column = getColumnModel().getColumn(i);
                column.setPreferredWidth(COLUMN_WIDTHS[i]);
            }

            setFillsViewportHeight(true);
        }

        public void setType(ObjectType type) {
            this.type = type;

            if ((type != null) && (type.getPropertyDefinitions() != null)) {
                propertyDefintions = new ArrayList<PropertyDefinition<?>>();
                for (PropertyDefinition<?> propDef : type.getPropertyDefinitions().values()) {
                    propertyDefintions.add(propDef);
                }

                Collections.sort(propertyDefintions, new Comparator<PropertyDefinition<?>>() {
                    public int compare(PropertyDefinition<?> pd1, PropertyDefinition<?> pd2) {
                        return pd1.getId().compareTo(pd2.getId());
                    }
                });
            } else {
                propertyDefintions = null;
            }

            ((AbstractTableModel) getModel()).fireTableDataChanged();
        }

        public ObjectType getType() {
            return type;
        }

        public List<PropertyDefinition<?>> getPropertyDefinitions() {
            return propertyDefintions;
        }

        static class PropertyDefinitionTableModel extends AbstractTableModel {

            private static final long serialVersionUID = 1L;

            private PropertyDefinitionTable table;

            public PropertyDefinitionTableModel(PropertyDefinitionTable table) {
                this.table = table;
            }

            public String getColumnName(int columnIndex) {
                return COLUMN_NAMES[columnIndex];
            }

            public int getColumnCount() {
                return COLUMN_NAMES.length;
            }

            public int getRowCount() {
                if (table.getPropertyDefinitions() == null) {
                    return 0;
                }

                return table.getPropertyDefinitions().size();
            }

            public Object getValueAt(int rowIndex, int columnIndex) {
                PropertyDefinition<?> propDef = table.getPropertyDefinitions().get(rowIndex);

                switch (columnIndex) {
                case 0:
                    return propDef.getDisplayName();
                case 1:
                    return propDef.getId();
                case 2:
                    return propDef.getDescription();
                case 3:
                    return propDef.getLocalNamespace();
                case 4:
                    return propDef.getLocalName();
                case 5:
                    return propDef.getQueryName();
                case 6:
                    return propDef.getPropertyType();
                case 7:
                    return propDef.getCardinality();
                case 8:
                    return propDef.getUpdatability();
                case 9:
                    return propDef.isQueryable();
                case 10:
                    return propDef.isRequired();
                case 11:
                    return propDef.isInherited();
                case 12:
                    return propDef.getDefaultValue();
                case 13:
                    return propDef.getChoices();
                }

                return null;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if ((columnIndex == 12) || (columnIndex == 13)) {
                    return Collection.class;
                }

                return super.getColumnClass(columnIndex);
            }
        }
    }
}