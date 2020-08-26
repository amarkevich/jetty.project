//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.util.resource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.URIUtil;

/**
 * A collection of resources (dirs).
 * Allows webapps to have multiple (static) sources.
 * The first resource in the collection is the main resource.
 * If a resource is not found in the main resource, it looks it up in
 * the order the resources were constructed.
 */
public class ResourceCollection extends Resource
{
    private List<Resource> _resources;

    /**
     * Instantiates an empty resource collection.
     * <p>
     * This constructor is used when configuring jetty-maven-plugin.
     */
    public ResourceCollection()
    {
        _resources = new ArrayList<>();
    }

    /**
     * Instantiates a new resource collection.
     *
     * @param resources the resources to be added to collection
     */
    public ResourceCollection(Resource... resources)
    {
        _resources = new ArrayList<>();
        for (Resource r : resources)
        {
            if (r == null)
            {
                continue;
            }
            if (r instanceof ResourceCollection)
            {
                _resources.addAll(((ResourceCollection)r).getResources());
            }
            else
            {
                assertResourceValid(r);
                _resources.add(r);
            }
        }
    }

    /**
     * Instantiates a new resource collection.
     *
     * @param resources the resources to be added to collection
     */
    public ResourceCollection(Collection<Resource> resources)
    {
        _resources = new ArrayList<>();
        _resources.addAll(resources);
    }

    /**
     * Instantiates a new resource collection.
     *
     * @param resources the resource strings to be added to collection
     */
    public ResourceCollection(String[] resources)
    {
        _resources = new ArrayList<>();

        if (resources == null || resources.length == 0)
        {
            return;
        }

        try
        {
            for (String strResource : resources)
            {
                if (strResource == null || strResource.length() == 0)
                {
                    throw new IllegalArgumentException("empty/null resource path not supported");
                }
                Resource resource = Resource.newResource(strResource);
                assertResourceValid(resource);
                _resources.add(resource);
            }

            if (_resources.isEmpty())
            {
                throw new IllegalArgumentException("resources cannot be empty or null");
            }
        }
        catch (RuntimeException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    /**
     * Instantiates a new resource collection.
     *
     * @param csvResources the string containing comma-separated resource strings
     * @throws IOException if any listed resource is not valid
     */
    public ResourceCollection(String csvResources) throws IOException
    {
        setResourcesAsCSV(csvResources);
    }

    /**
     * Retrieves the resource collection's resources.
     *
     * @return the resource collection
     */
    public List<Resource> getResources()
    {
        return _resources;
    }

    /**
     * Sets the resource collection's resources.
     *
     * @param res the resources to set
     */
    public void setResources(List<Resource> res)
    {
        _resources = new ArrayList<>();
        if (res.isEmpty())
        {
            return;
        }

        _resources.addAll(res);
    }

    /**
     * Sets the resource collection's resources.
     *
     * @param resources the new resource array
     */
    public void setResources(Resource[] resources)
    {
        if (resources == null || resources.length == 0)
        {
            _resources = null;
            return;
        }

        List<Resource> res = new ArrayList<>();
        for (Resource resource : resources)
        {
            assertResourceValid(resource);
            res.add(resource);
        }

        setResources(res);
    }

    /**
     * Sets the resources as string of comma-separated values.
     * This method should be used when configuring jetty-maven-plugin.
     *
     * @param csvResources the comma-separated string containing
     * one or more resource strings.
     * @throws IOException if unable resource declared is not valid
     */
    public void setResourcesAsCSV(String csvResources) throws IOException
    {
        if (StringUtil.isBlank(csvResources))
        {
            throw new IllegalArgumentException("CSV String is blank");
        }

        List<Resource> resources = Resource.fromList(csvResources, false);
        if (resources.isEmpty())
        {
            throw new IllegalArgumentException("CSV String contains no entries");
        }
        List<Resource> ret = new ArrayList<>();
        for (Resource resource : resources)
        {
            assertResourceValid(resource);
            ret.add(resource);
        }
        setResources(ret);
    }

    /**
     * @param path The path segment to add
     * @return The contained resource (found first) in the collection of resources
     */
    @Override
    public Resource addPath(String path) throws IOException
    {
        assertResourcesSet();

        if (path == null)
        {
            throw new MalformedURLException("null path");
        }

        if (path.length() == 0 || URIUtil.SLASH.equals(path))
        {
            return this;
        }

        // Attempt a simple (single) Resource lookup that exists
        for (Resource res : _resources)
        {
            Resource fileResource = res.addPath(path);
            if (fileResource.exists())
            {
                if (!fileResource.isDirectory())
                {
                    return fileResource;
                }
            }
        }

        // Create a list of potential resource for directories of this collection
        ArrayList<Resource> potentialResources = null;
        for (Resource res : _resources)
        {
            Resource r = res.addPath(path);
            if (r.exists() && r.isDirectory())
            {
                if (potentialResources == null)
                {
                    potentialResources = new ArrayList<>();
                }

                potentialResources.add(r);
            }
        }

        if (potentialResources == null || potentialResources.isEmpty())
        {
            throw new MalformedURLException("path does not result in Resource: " + path);
        }

        if (potentialResources.size() == 1)
        {
            return potentialResources.get(0);
        }

        return new ResourceCollection(potentialResources);
    }

    @Override
    public boolean delete() throws SecurityException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean exists()
    {
        assertResourcesSet();

        return true;
    }

    @Override
    public File getFile() throws IOException
    {
        assertResourcesSet();

        for (Resource r : _resources)
        {
            File f = r.getFile();
            if (f != null)
            {
                return f;
            }
        }
        return null;
    }

    @Override
    public InputStream getInputStream() throws IOException
    {
        assertResourcesSet();

        for (Resource r : _resources)
        {
            InputStream is = r.getInputStream();
            if (is != null)
            {
                return is;
            }
        }
        return null;
    }

    @Override
    public ReadableByteChannel getReadableByteChannel() throws IOException
    {
        assertResourcesSet();

        for (Resource r : _resources)
        {
            ReadableByteChannel channel = r.getReadableByteChannel();
            if (channel != null)
            {
                return channel;
            }
        }
        return null;
    }

    @Override
    public String getName()
    {
        assertResourcesSet();

        for (Resource r : _resources)
        {
            String name = r.getName();
            if (name != null)
            {
                return name;
            }
        }
        return null;
    }

    @Override
    public URI getURI()
    {
        assertResourcesSet();

        for (Resource r : _resources)
        {
            URI uri = r.getURI();
            if (uri != null)
            {
                return uri;
            }
        }
        return null;
    }

    @Override
    public boolean isDirectory()
    {
        assertResourcesSet();

        return true;
    }

    @Override
    public long lastModified()
    {
        assertResourcesSet();

        for (Resource r : _resources)
        {
            long lm = r.lastModified();
            if (lm != -1)
            {
                return lm;
            }
        }
        return -1;
    }

    @Override
    public long length()
    {
        return -1;
    }

    /**
     * @return The list of resource names(merged) contained in the collection of resources.
     */
    @Override
    public String[] list()
    {
        assertResourcesSet();

        HashSet<String> set = new HashSet<>();
        for (Resource r : _resources)
        {
            Collections.addAll(set, r.list());
        }

        return (String[])set.stream().sorted().toArray();
    }

    @Override
    public void close()
    {
        assertResourcesSet();

        for (Resource r : _resources)
        {
            r.close();
        }
    }

    @Override
    public boolean renameTo(Resource dest) throws SecurityException
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void copyTo(File destination)
        throws IOException
    {
        assertResourcesSet();

        // Copy in reverse order
        for (int r = _resources.size(); r-- > 0; )
        {
            _resources.get(r).copyTo(destination);
        }
    }

    /**
     * @return the list of resources separated by a path separator
     */
    @Override
    public String toString()
    {
        if (_resources.isEmpty())
        {
            return "[]";
        }

        return String.valueOf(_resources);
    }

    @Override
    public boolean isContainedIn(Resource r)
    {
        // TODO could look at implementing the semantic of is this collection a subset of the Resource r?
        return false;
    }

    private void assertResourcesSet()
    {
        if (_resources == null || _resources.isEmpty())
        {
            throw new IllegalStateException("*resources* not set.");
        }
    }

    private void assertResourceValid(Resource resource)
    {
        if (resource == null)
        {
            throw new IllegalStateException("Null resource not supported");
        }

        if (!resource.exists() || !resource.isDirectory())
        {
            throw new IllegalArgumentException(resource + " is not an existing directory.");
        }
    }
}
