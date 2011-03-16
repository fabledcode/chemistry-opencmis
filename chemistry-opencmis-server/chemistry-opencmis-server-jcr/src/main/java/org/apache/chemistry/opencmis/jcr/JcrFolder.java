/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.chemistry.opencmis.jcr;

import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.data.Properties;
import org.apache.chemistry.opencmis.commons.data.PropertyData;
import org.apache.chemistry.opencmis.commons.definitions.PropertyDefinition;
import org.apache.chemistry.opencmis.commons.definitions.TypeDefinition;
import org.apache.chemistry.opencmis.commons.enums.Action;
import org.apache.chemistry.opencmis.commons.enums.BaseTypeId;
import org.apache.chemistry.opencmis.commons.enums.Updatability;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;
import org.apache.chemistry.opencmis.commons.exceptions.CmisConstraintException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisObjectNotFoundException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisRuntimeException;
import org.apache.chemistry.opencmis.commons.exceptions.CmisStorageException;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.FailedToDeleteDataImpl;
import org.apache.chemistry.opencmis.commons.impl.dataobjects.PropertiesImpl;
import org.apache.chemistry.opencmis.commons.impl.server.ObjectInfoImpl;
import org.apache.chemistry.opencmis.jcr.util.FilterIterator;
import org.apache.chemistry.opencmis.jcr.util.Predicate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import javax.jcr.Binary;
import javax.jcr.Node;
import javax.jcr.NodeIterator;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.nodetype.NodeType;
import javax.jcr.version.Version;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;


/**
 * Instances of this class represent a cmis:folder backed by an underlying JCR <code>Node</code>. 
 */
public class JcrFolder extends JcrNode {
    private static final Log log = LogFactory.getLog(JcrFolder.class);

    public JcrFolder(Node node, TypeManager typeManager, PathManager pathManager, JcrNodeFactory nodeFactory) {
        super(node, typeManager, pathManager, nodeFactory);
    }

    /**
     * See CMIS 1.0 section 2.2.3.1 getChildren
     * 
     * @return  Iterator of <code>JcrNode</code>. Children which are created in the checked out
     *      state are left out from the iterator.
     * @throws CmisRuntimeException
     */
    public Iterator<JcrNode> getNodes() {
        try {
            final NodeIterator nodes = getNode().getNodes();

            Iterator<JcrNode> jcrNodes = new Iterator<JcrNode>() {
                public boolean hasNext() {
                    return nodes.hasNext();
                }

                public JcrNode next() {
                    return create(nodes.nextNode());
                }

                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };

            // Filter out nodes which are checked out and do not have a version history (i.e. only a root version)
            // These are created with VersioningState checkedout and not yet checked in.
            return new FilterIterator<JcrNode>(jcrNodes, new Predicate<JcrNode>() {
                public boolean evaluate(JcrNode node) {
                    try {
                        if (node.isVersionable()) {
                            Version baseVersion = getBaseVersion(node.getNode());
                            return baseVersion.getPredecessors().length > 0;
                        }
                        else {
                            return true;
                        }
                    }
                    catch (RepositoryException e) {
                        log.debug(e.getMessage(), e);
                        throw new CmisRuntimeException(e.getMessage(), e);
                    }
                }
            });

        }
        catch (RepositoryException e) {
            log.debug(e.getMessage(), e);
            throw new CmisRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * See CMIS 1.0 section 2.2.4.1 createDocument
     *
     * @throws CmisStorageException
     */
    public JcrNode addNode(String name, String typeId, Properties properties, ContentStream contentStream,
            VersioningState versioningState) {
        
        try {
            Node fileNode = getNode().addNode(name, NodeType.NT_FILE);
            if (versioningState != VersioningState.NONE) {
                fileNode.addMixin(NodeType.MIX_SIMPLE_VERSIONABLE);
            }

            Node contentNode = fileNode.addNode(Node.JCR_CONTENT, NodeType.NT_RESOURCE);
            contentNode.addMixin(NodeType.MIX_CREATED);

            // compile the properties
            setProperties(contentNode, typeId, properties);

            // write content, if available
            Binary binary = contentStream == null || contentStream.getStream() == null
                    ? JcrBinary.EMPTY
                    : new JcrBinary(new BufferedInputStream(contentStream.getStream()));
            try {
                contentNode.setProperty(Property.JCR_DATA, binary);
                if (contentStream != null && contentStream.getMimeType() != null) {
                    contentNode.setProperty(Property.JCR_MIMETYPE, contentStream.getMimeType());
                }
            }
            finally {
                binary.dispose();
            }

            fileNode.getSession().save();
            JcrNode jcrFileNode = create(fileNode);
            if (versioningState == VersioningState.NONE) {
                return jcrFileNode;
            }

            JcrVersionBase jcrVersion = jcrFileNode.asVersion();
            if (versioningState == VersioningState.MINOR || versioningState == VersioningState.MAJOR) {
                return jcrVersion.checkin(null, null, "auto checkin");
            }
            else {
                return jcrVersion.getPwc();
            }
        }
        catch (RepositoryException e) {
            log.debug(e.getMessage(), e);
            throw new CmisStorageException(e.getMessage(), e);
        }
        catch (IOException e) {
            log.debug(e.getMessage(), e);
            throw new CmisStorageException(e.getMessage(), e);
        }
    }

    /**
     * See CMIS 1.0 section 2.2.4.2 createDocumentFromSource
     *
     * @throws CmisStorageException
     */
    public JcrNode addNodeFromSource(JcrDocument source, Properties properties) {
        try {
            String destPath = PathManager.createCmisPath(getNode().getPath(), source.getName());
            Session session = getNode().getSession();

            session.getWorkspace().copy(source.getNode().getPath(), destPath);  
            JcrNode jcrNode = create(session.getNode(destPath));

            // overlay new properties
            if (properties != null && properties.getProperties() != null) {
                updateProperties(jcrNode.getNode(), jcrNode.getTypeId(), properties);
            }

            session.save();
            return jcrNode;
        }
        catch (RepositoryException e) {
            log.debug(e.getMessage(), e);
            throw new CmisStorageException(e.getMessage(), e);
        }
    }

    /**
     * See CMIS 1.0 section 2.2.4.3 createFolder
     *
     * @throws CmisStorageException
     */
    public JcrNode addFolder(String name, String typeId, Properties properties) {
        try {
            Node node = getNode().addNode(name, NodeType.NT_FOLDER);
            node.addMixin(NodeType.MIX_CREATED);
            node.addMixin(NodeType.MIX_LAST_MODIFIED);

            // compile the properties
            setProperties(node, typeId, properties);

            node.getSession().save();
            return create(node);
        }
        catch (RepositoryException e) {
            log.debug(e.getMessage(), e);
            throw new CmisStorageException(e.getMessage(), e);
        }
    }

    /**
     * See CMIS 1.0 section 2.2.4.14 deleteObject
     *
     * @throws CmisRuntimeException
     */
    @Override
    public void delete(boolean allVersions, boolean isPwc) {
        try {
            if (getNode().hasNodes()) {
                throw new CmisConstraintException("Folder is not empty!");
            }
            else {
                super.delete(allVersions, isPwc);
            }
        }
        catch (RepositoryException e) {
            log.debug(e.getMessage(), e);
            throw new CmisRuntimeException(e.getMessage(), e);
        }
    }

    /**
     * See CMIS 1.0 section 2.2.4.15 deleteTree
     */
    public FailedToDeleteDataImpl deleteTree() {
        FailedToDeleteDataImpl result = new FailedToDeleteDataImpl();

        String id = getId();
        try {
            Session session = getNode().getSession();
            getNode().remove();
            session.save();
            result.setIds(Collections.<String>emptyList());
        }
        catch (RepositoryException e) {
            result.setIds(Collections.singletonList(id));
        }

        return result;
    }

    //------------------------------------------< protected >---

    @Override
    protected void compileProperties(PropertiesImpl properties, Set<String> filter, ObjectInfoImpl objectInfo)
            throws RepositoryException {

        super.compileProperties(properties, filter, objectInfo);

        objectInfo.setHasContent(false);
        objectInfo.setSupportsDescendants(true);
        objectInfo.setSupportsFolderTree(true);

        String typeId = getTypeIdInternal();

        addPropertyString(properties, typeId, filter, PropertyIds.PATH, pathManager.getPath(getNode()));

        // folder properties
        if (pathManager.isRoot(getNode())) {
            objectInfo.setHasParent(false);
        }
        else {
            objectInfo.setHasParent(true);
            addPropertyId(properties, typeId, filter, PropertyIds.PARENT_ID, getParent().getObjectId());
        }
    }

    @Override
    protected Set<Action> compileAllowableActions(Set<Action> aas) {
        Set<Action> result = super.compileAllowableActions(aas);
        setAction(result, Action.CAN_GET_DESCENDANTS, true);
        setAction(result, Action.CAN_GET_CHILDREN, true);
        setAction(result, Action.CAN_GET_FOLDER_PARENT, !pathManager.isRoot(getNode()));
        setAction(result, Action.CAN_GET_OBJECT_PARENTS, !pathManager.isRoot(getNode()));
        setAction(result, Action.CAN_GET_FOLDER_TREE, true);
        setAction(result, Action.CAN_CREATE_DOCUMENT, true);
        setAction(result, Action.CAN_CREATE_FOLDER, true);
        setAction(result, Action.CAN_DELETE_TREE, true);
        return result;
    }

    @Override
    protected Node getContextNode() {
        return getNode();
    }

    @Override
    protected String getObjectId() throws RepositoryException {
        return isRoot()
                ? PathManager.CMIS_ROOT_ID
                : super.getObjectId();
    }

    @Override
    protected BaseTypeId getBaseTypeId() {
        return BaseTypeId.CMIS_FOLDER;
    }

    @Override
    protected String getTypeIdInternal() {
        return TypeManager.FOLDER_TYPE_ID;
    }

    //------------------------------------------< private >---

    private void setProperties(Node node, String typeId, Properties properties) {
        if (properties == null || properties.getProperties() == null) {
            throw new CmisConstraintException("No properties!");
        }

        Set<String> addedProps = new HashSet<String>();

        // get the property definitions
        TypeDefinition type = typeManager.getType(typeId);
        if (type == null) {
            throw new CmisObjectNotFoundException("Type '" + typeId + "' is unknown!");
        }

        try {
            // check if all required properties are there
            for (PropertyData<?> prop : properties.getProperties().values()) {
                PropertyDefinition<?> propDef = type.getPropertyDefinitions().get(prop.getId());

                // do we know that property?
                if (propDef == null) {
                    throw new CmisConstraintException("Property '" + prop.getId() + "' is unknown!");
                }

                // skip type id
                if (propDef.getId().equals(PropertyIds.OBJECT_TYPE_ID)) {
                    addedProps.add(prop.getId());
                    continue;
                }

                // can it be set?
                if (propDef.getUpdatability() == Updatability.READONLY) {
                    throw new CmisConstraintException("Property '" + prop.getId() + "' is readonly!");
                }

                // empty properties are invalid
                if (PropertyHelper.isPropertyEmpty(prop)) {
                    throw new CmisConstraintException("Property '" + prop.getId() + "' must not be empty!");
                }

                // add it
                JcrConverter.setProperty(node, prop);
                addedProps.add(prop.getId());
            }

            // check if required properties are missing and try to add default values if defined
            for (PropertyDefinition<?> propDef : type.getPropertyDefinitions().values()) {
                if (!addedProps.contains(propDef.getId()) && propDef.getUpdatability() != Updatability.READONLY) {
                    PropertyData<?> prop = PropertyHelper.getDefaultValue(propDef);
                    if (prop == null && propDef.isRequired()) {
                        throw new CmisConstraintException("Property '" + propDef.getId() + "' is required!");
                    }
                    else if (prop != null) {
                        JcrConverter.setProperty(node, prop);
                    }
                }
            }
        }
        catch (RepositoryException e) {
            log.debug(e.getMessage(), e);
            throw new CmisStorageException(e.getMessage(), e);
        }
    }
}