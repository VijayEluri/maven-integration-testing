package org.apache.maven.it;

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

import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mortbay.jetty.Handler;
import org.mortbay.jetty.Request;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;

/**
 * This is a test set for <a href="http://jira.codehaus.org/browse/MNG-4343">MNG-4343</a>.
 * 
 * @author Benjamin Bentmann
 * @version $Id$
 */
public class MavenITmng4343MissingReleaseUpdatePolicyTest
    extends AbstractMavenIntegrationTestCase
{

    private Server server;

    private List requestedUris;

    private boolean blockAccess;

    public MavenITmng4343MissingReleaseUpdatePolicyTest()
    {
        super( "[3.0-alpha-3,)" );
    }

    protected void setUp()
        throws Exception
    {
        Handler repoHandler = new AbstractHandler()
        {
            public void handle( String target, HttpServletRequest request, HttpServletResponse response, int dispatch )
                throws IOException, ServletException
            {
                System.out.println( "Handling " + request.getMethod() + " " + request.getRequestURL() );

                if ( request.getRequestURI().startsWith( "/org/apache/maven/its/mng4343" ) )
                {
                    requestedUris.add( request.getRequestURI() );
                }

                if ( blockAccess )
                {
                    response.setStatus( HttpServletResponse.SC_NOT_FOUND );
                }
                else
                {
                    PrintWriter writer = response.getWriter();
    
                    response.setStatus( HttpServletResponse.SC_OK );
    
                    if ( request.getRequestURI().endsWith( ".pom" ) )
                    {
                        writer.println( "<project>" );
                        writer.println( "  <modelVersion>4.0.0</modelVersion>" );
                        writer.println( "  <groupId>org.apache.maven.its.mng4343</groupId>" );
                        writer.println( "  <artifactId>dep</artifactId>" );
                        writer.println( "  <version>0.1</version>" );
                        writer.println( "</project>" );
                    }
                    else if ( request.getRequestURI().endsWith( ".jar" ) )
                    {
                        writer.println( "empty" );
                    }
                    else if ( request.getRequestURI().endsWith( ".md5" ) || request.getRequestURI().endsWith( ".sha1" ) )
                    {
                        response.setStatus( HttpServletResponse.SC_NOT_FOUND );
                    }
                }

                ( (Request) request ).setHandled( true );
            }
        };

        server = new Server( 0 );
        server.setHandler( repoHandler );
        server.start();

        requestedUris = new ArrayList();
    }

    protected void tearDown()
        throws Exception
    {
        if ( server != null )
        {
            server.stop();
            server = null;
        }
        requestedUris = null;
    }

    /**
     * Verify that checking for *missing* release artifacts respects the update policy that is configured in the
     * release section for the respective repository, in this case "always".
     */
    public void testitAlways()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/mng-4343" );

        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.setAutoclean( false );
        verifier.deleteArtifacts( "org.apache.maven.its.mng4343" );
        verifier.getCliOptions().add( "-s" );
        verifier.getCliOptions().add( "settings.xml" );

        Properties filterProps = verifier.newDefaultFilterProperties();
        filterProps.setProperty( "@updates@", "always" );
        filterProps.setProperty( "@port@", Integer.toString( server.getConnectors()[0].getLocalPort() ) );
        verifier.filterFile( "settings-template.xml", "settings.xml", "UTF-8", filterProps );

        blockAccess = true;

        verifier.setLogFileName( "log-always-1.txt" );
        try
        {
            verifier.executeGoal( "validate" );
            verifier.verifyErrorFreeLog();
            fail( "Build succeeded despite missing dependency" );
        }
        catch ( VerificationException e )
        {
            // expected
        }

        assertTrue( requestedUris.toString(), requestedUris.contains( "/org/apache/maven/its/mng4343/dep/0.1/dep-0.1.jar" ) );
        requestedUris.clear();

        blockAccess = false;

        verifier.setLogFileName( "log-always-2.txt" );
        verifier.executeGoal( "validate" );
        verifier.verifyErrorFreeLog();

        assertTrue( requestedUris.toString(), requestedUris.contains( "/org/apache/maven/its/mng4343/dep/0.1/dep-0.1.jar" ) );
        assertTrue( requestedUris.toString(), requestedUris.contains( "/org/apache/maven/its/mng4343/dep/0.1/dep-0.1.pom" ) );
        verifier.assertArtifactPresent( "org.apache.maven.its.mng4343", "dep", "0.1", "jar" );
        verifier.assertArtifactPresent( "org.apache.maven.its.mng4343", "dep", "0.1", "pom" );

        verifier.resetStreams();
    }

    /**
     * Verify that checking for *missing* release artifacts respects the update policy that is configured in the
     * release section for the respective repository, in this case "never", unless overriden from the CLI via -U.
     */
    public void testitNever()
        throws Exception
    {
        File testDir = ResourceExtractor.simpleExtractResources( getClass(), "/mng-4343" );

        Verifier verifier = new Verifier( testDir.getAbsolutePath() );
        verifier.setAutoclean( false );
        verifier.deleteArtifacts( "org.apache.maven.its.mng4343" );
        verifier.getCliOptions().add( "-s" );
        verifier.getCliOptions().add( "settings.xml" );

        Properties filterProps = verifier.newDefaultFilterProperties();
        filterProps.setProperty( "@updates@", "never" );
        filterProps.setProperty( "@port@", Integer.toString( server.getConnectors()[0].getLocalPort() ) );
        verifier.filterFile( "settings-template.xml", "settings.xml", "UTF-8", filterProps );

        blockAccess = true;

        verifier.setLogFileName( "log-never-1.txt" );
        try
        {
            verifier.executeGoal( "validate" );
            verifier.verifyErrorFreeLog();
            fail( "Build succeeded despite missing dependency" );
        }
        catch ( VerificationException e )
        {
            // expected
        }

        assertTrue( requestedUris.toString(), requestedUris.contains( "/org/apache/maven/its/mng4343/dep/0.1/dep-0.1.jar" ) );
        requestedUris.clear();

        blockAccess = false;

        verifier.setLogFileName( "log-never-2.txt" );
        try
        {
            verifier.executeGoal( "validate" );
            verifier.verifyErrorFreeLog();
            fail( "Remote repository was accessed despite updatePolicy=never" );
        }
        catch ( VerificationException e )
        {
            // expected
        }

        assertEquals( new ArrayList(), requestedUris );
        verifier.assertArtifactNotPresent( "org.apache.maven.its.mng4343", "dep", "0.1", "jar" );
        verifier.assertArtifactNotPresent( "org.apache.maven.its.mng4343", "dep", "0.1", "pom" );

        verifier.setLogFileName( "log-never-3.txt" );
        verifier.getCliOptions().add( "-U" );
        verifier.executeGoal( "validate" );
        verifier.verifyErrorFreeLog();

        assertTrue( requestedUris.toString(), requestedUris.contains( "/org/apache/maven/its/mng4343/dep/0.1/dep-0.1.jar" ) );
        assertTrue( requestedUris.toString(), requestedUris.contains( "/org/apache/maven/its/mng4343/dep/0.1/dep-0.1.pom" ) );
        verifier.assertArtifactPresent( "org.apache.maven.its.mng4343", "dep", "0.1", "jar" );
        verifier.assertArtifactPresent( "org.apache.maven.its.mng4343", "dep", "0.1", "pom" );

        requestedUris.clear();

        verifier.setLogFileName( "log-never-4.txt" );
        verifier.getCliOptions().add( "-U" );
        verifier.executeGoal( "validate" );
        verifier.verifyErrorFreeLog();

        assertEquals( new ArrayList(), requestedUris );

        verifier.resetStreams();
    }

}