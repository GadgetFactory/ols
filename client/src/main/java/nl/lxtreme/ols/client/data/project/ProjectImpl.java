/*
 * OpenBench LogicSniffer / SUMP project 
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or (at
 * your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin St, Fifth Floor, Boston, MA 02110, USA
 *
 * Copyright (C) 2006-2010 Michael Poppitz, www.sump.org
 * Copyright (C) 2010 J.W. Janssen, www.lxtreme.nl
 */
package nl.lxtreme.ols.client.data.project;


import java.beans.*;
import java.io.*;
import java.util.*;
import java.util.logging.*;

import nl.lxtreme.ols.api.*;
import nl.lxtreme.ols.api.data.*;
import nl.lxtreme.ols.api.data.project.*;
import nl.lxtreme.ols.client.data.settings.*;


/**
 * Denotes a project implementation.
 */
final class ProjectImpl implements Project, ProjectProperties
{
  // CONSTANTS

  private static final Logger LOG = Logger.getLogger( ProjectImpl.class.getName() );

  // VARIABLES

  private final PropertyChangeSupport propertyChangeSupport;

  private String name;
  private final String[] channelLabels;
  private final Long[] cursors;
  private final Map<String, UserSettings> settings;
  private CapturedData capturedData;
  private boolean changed;
  private boolean cursorsEnabled;
  private Date lastModified;
  private String sourceVersion;
  private File filename;

  // CONSTRUCTORS

  /**
   * Creates a new ProjectImpl.
   */
  public ProjectImpl()
  {
    super();

    this.propertyChangeSupport = new PropertyChangeSupport( this );

    this.cursors = new Long[CapturedData.MAX_CURSORS];
    this.channelLabels = new String[CapturedData.MAX_CHANNELS];

    this.changed = false;
    this.cursorsEnabled = false;

    this.settings = new HashMap<String, UserSettings>();
  }

  // METHODS

  /**
   * Adds the given listener to the list of property change listeners.
   * 
   * @param aListener
   *          a property change listener, cannot be <code>null</code>.
   */
  public void addPropertyChangeListener( final PropertyChangeListener aListener )
  {
    this.propertyChangeSupport.addPropertyChangeListener( aListener );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#getCapturedData()
   */
  @Override
  public CapturedData getCapturedData()
  {
    return this.capturedData;
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#getChannelLabels()
   */
  @Override
  public String[] getChannelLabels()
  {
    return this.channelLabels;
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#getCursorPositions()
   */
  @Override
  public Long[] getCursorPositions()
  {
    return this.cursors;
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#getFilename()
   */
  @Override
  public File getFilename()
  {
    return this.filename;
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#getLastModified()
   */
  @Override
  public Date getLastModified()
  {
    return this.lastModified;
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#getName()
   */
  @Override
  public String getName()
  {
    return this.name;
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#getSettings(java.lang.String)
   */
  @Override
  public UserSettings getSettings( final String aName )
  {
    UserSettings result = this.settings.get( aName );
    if ( result == null )
    {
      result = new UserSettingsImpl( aName );
      this.settings.put( aName, result );
    }
    return result;
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#getSourceVersion()
   */
  @Override
  public String getSourceVersion()
  {
    return this.sourceVersion;
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#isChanged()
   */
  @Override
  public boolean isChanged()
  {
    return this.changed;
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#isCursorsEnabled()
   */
  @Override
  public boolean isCursorsEnabled()
  {
    return this.cursorsEnabled;
  }

  /**
   * Removes the given listener from the list of property change listeners.
   * 
   * @param aListener
   *          a property change listener, cannot be <code>null</code>.
   */
  public void removePropertyChangeListener( final PropertyChangeListener aListener )
  {
    this.propertyChangeSupport.removePropertyChangeListener( aListener );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#setCapturedData(nl.lxtreme.ols.api.data.CapturedData)
   */
  @Override
  public void setCapturedData( final CapturedData aCapturedData )
  {
    final CapturedData old = this.capturedData;
    this.capturedData = aCapturedData;

    this.propertyChangeSupport.firePropertyChange( PROPERTY_CAPTURED_DATA, old, aCapturedData );

    // Mark this project as modified...
    setChanged( true );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#setChanged(boolean)
   */
  @Override
  public void setChanged( final boolean aChanged )
  {
    final boolean old = this.changed;
    this.changed = aChanged;

    this.propertyChangeSupport.firePropertyChange( PROPERTY_CHANGED, old, aChanged );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#setChannelLabels(java.lang.String[])
   */
  @Override
  public void setChannelLabels( final String... aChannelLabels )
  {
    if ( aChannelLabels == null )
    {
      throw new IllegalArgumentException( "Channel labels cannot be null!" );
    }

    final String[] old = Arrays.copyOf( this.channelLabels, this.channelLabels.length );
    System.arraycopy( aChannelLabels, 0, this.channelLabels, 0, aChannelLabels.length );

    this.propertyChangeSupport.firePropertyChange( PROPERTY_CHANNEL_LABELS, old, aChannelLabels );

    // Mark this project as modified...
    setChanged( true );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#setCursorPositions(long[])
   */
  @Override
  public void setCursorPositions( final Long... aCursors )
  {
    if ( aCursors == null )
    {
      throw new IllegalArgumentException( "Cursors cannot be null!" );
    }

    final Long[] old = Arrays.copyOf( this.cursors, this.cursors.length );
    System.arraycopy( aCursors, 0, this.cursors, 0, aCursors.length );

    this.propertyChangeSupport.firePropertyChange( PROPERTY_CURSORS, old, aCursors );

    // Mark this project as modified...
    setChanged( true );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#setCursorsEnabled(boolean)
   */
  @Override
  public void setCursorsEnabled( final boolean aEnabled )
  {
    final boolean old = this.cursorsEnabled;
    this.cursorsEnabled = aEnabled;

    this.propertyChangeSupport.firePropertyChange( PROPERTY_CURSORS_ENABLED, old, aEnabled );

    // Mark this project as modified...
    setChanged( true );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#setFilename(java.io.File)
   */
  @Override
  public void setFilename( final File aFilename )
  {
    final File old = this.filename;
    this.filename = aFilename;

    this.propertyChangeSupport.firePropertyChange( PROPERTY_FILENAME, old, aFilename );

    // We don't mark the project as saved; as this is probably a bit weird: we
    // save a new project, thereby knowing its filename, and yet we're marking
    // it immediately as changed...
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#setLastModified(java.util.Date)
   */
  @Override
  public void setLastModified( final Date aLastModified )
  {
    final Date old = this.lastModified;
    this.lastModified = aLastModified;

    this.propertyChangeSupport.firePropertyChange( PROPERTY_LAST_MODIFIED, old, aLastModified );

    // Mark this project as modified...
    setChanged( true );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#setName(java.lang.String)
   */
  @Override
  public void setName( final String aName )
  {
    final String old = this.name;
    this.name = aName;

    this.propertyChangeSupport.firePropertyChange( PROPERTY_NAME, old, aName );

    // Mark this project as modified...
    setChanged( true );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#setSettings(nl.lxtreme.ols.api.UserSettings)
   */
  @Override
  public void setSettings( final UserSettings aSettings )
  {
    if ( aSettings == null )
    {
      throw new IllegalArgumentException( "Settings cannot be null!" );
    }

    final UserSettings old = this.settings.get( aSettings.getName() );
    this.settings.put( aSettings.getName(), aSettings );

    this.propertyChangeSupport.firePropertyChange( PROPERTY_SETTINGS, old, aSettings );

    // Mark this project as modified...
    setChanged( true );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#setSourceVersion(java.lang.String)
   */
  @Override
  public void setSourceVersion( final String aSourceVersion )
  {
    final String old = this.sourceVersion;
    this.sourceVersion = aSourceVersion;

    this.propertyChangeSupport.firePropertyChange( PROPERTY_SOURCE_VERSION, old, aSourceVersion );

    // Mark this project as modified...
    setChanged( true );
  }

  /**
   * @see nl.lxtreme.ols.api.data.project.Project#visit(nl.lxtreme.ols.api.data.project.ProjectVisitor)
   */
  @Override
  public void visit( final ProjectVisitor aVisitor )
  {
    final List<UserSettings> userSettings = new ArrayList<UserSettings>( this.settings.values() );
    for ( UserSettings settings : userSettings )
    {
      try
      {
        aVisitor.visit( settings );
      }
      catch ( Exception exception )
      {
        LOG.log( Level.INFO, "Exception during visiting project! Continuing anyway...", exception );
      }
    }
  }

  /**
   * Returns the current set of property change listeners.
   * 
   * @return an array of property change listeners, never <code>null</code>.
   */
  final PropertyChangeListener[] getPropertyChangeListeners()
  {
    return this.propertyChangeSupport.getPropertyChangeListeners();
  }
}
