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
package nl.lxtreme.ols.client;


import java.awt.*;
import java.io.*;
import java.util.*;
import java.util.List;
import java.util.logging.*;

import javax.swing.*;
import javax.swing.event.*;

import nl.lxtreme.ols.api.data.*;
import nl.lxtreme.ols.api.data.export.*;
import nl.lxtreme.ols.api.data.project.*;
import nl.lxtreme.ols.api.devices.*;
import nl.lxtreme.ols.api.tools.*;
import nl.lxtreme.ols.client.action.*;
import nl.lxtreme.ols.client.action.manager.*;
import nl.lxtreme.ols.client.data.*;
import nl.lxtreme.ols.client.diagram.*;
import nl.lxtreme.ols.client.diagram.settings.*;
import nl.lxtreme.ols.util.*;

import org.osgi.framework.*;


/**
 * Denotes a front-end controller for the client.
 */
public final class ClientController implements ActionProvider, CaptureCallback, AnalysisCallback
{
  // INNER TYPES

  /**
   * Provides a default tool context implementation.
   */
  static final class DefaultToolContext implements ToolContext
  {
    // VARIABLES

    private final int startSampleIdx;
    private final int endSampleIdx;

    // CONSTRUCTORS

    /**
     * Creates a new DefaultToolContext instance.
     * 
     * @param aStartSampleIdx
     *          the starting sample index;
     * @param aEndSampleIdx
     *          the ending sample index.
     */
    public DefaultToolContext( final int aStartSampleIdx, final int aEndSampleIdx )
    {
      this.startSampleIdx = aStartSampleIdx;
      this.endSampleIdx = aEndSampleIdx;
    }

    /**
     * @see nl.lxtreme.ols.api.tools.ToolContext#getEndSampleIndex()
     */
    @Override
    public int getEndSampleIndex()
    {
      return this.endSampleIdx;
    }

    /**
     * @see nl.lxtreme.ols.api.tools.ToolContext#getLength()
     */
    @Override
    public int getLength()
    {
      return Math.max( 0, this.endSampleIdx - this.startSampleIdx );
    }

    /**
     * @see nl.lxtreme.ols.api.tools.ToolContext#getStartSampleIndex()
     */
    @Override
    public int getStartSampleIndex()
    {
      return this.startSampleIdx;
    }
  }

  // CONSTANTS

  private static final Logger LOG = Logger.getLogger( ClientController.class.getName() );

  // VARIABLES

  private final ActionManager actionManager;
  private final BundleContext bundleContext;
  private final DataContainer dataContainer;
  private final EventListenerList evenListeners;
  private final ProjectManager projectManager;
  private final Host host;

  private MainFrame mainFrame;

  private volatile DeviceController currentDevCtrl;

  // CONSTRUCTORS

  /**
   * Creates a new ClientController instance.
   */
  public ClientController( final BundleContext aBundleContext, final Host aHost, final ProjectManager aProjectManager )
  {
    this.bundleContext = aBundleContext;
    this.host = aHost;
    this.projectManager = aProjectManager;

    this.dataContainer = new DataContainer( this.projectManager );
    this.actionManager = new ActionManager();
    this.evenListeners = new EventListenerList();

    fillActionManager( this.actionManager );
  }

  // METHODS

  /**
   * Adds a cursor change listener.
   * 
   * @param aListener
   *          the listener to add, cannot be <code>null</code>.
   */
  public void addCursorChangeListener( final DiagramCursorChangeListener aListener )
  {
    this.evenListeners.add( DiagramCursorChangeListener.class, aListener );
  }

  /**
   * Adds the given device controller to this controller.
   * 
   * @param aDeviceController
   *          the device controller to add, cannot be <code>null</code>.
   */
  public void addDevice( final DeviceController aDeviceController )
  {
    if ( this.mainFrame != null )
    {
      if ( this.mainFrame.addDeviceMenuItem( aDeviceController ) )
      {
        this.currentDevCtrl = aDeviceController;
      }
    }

    updateActions();
  }

  /**
   * Adds the given exporter to this controller.
   * 
   * @param aExporter
   *          the exporter to add, cannot be <code>null</code>.
   */
  public void addExporter( final Exporter aExporter )
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.addExportMenuItem( aExporter.getName() );
    }

    updateActions();
  }

  /**
   * Adds the given tool to this controller.
   * 
   * @param aTool
   *          the tool to add, cannot be <code>null</code>.
   */
  public void addTool( final Tool aTool )
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.addToolMenuItem( aTool.getName() );
    }

    updateActions();
  }

  /**
   * @see nl.lxtreme.ols.api.tools.AnalysisCallback#analysisAborted(java.lang.String)
   */
  @Override
  public void analysisAborted( final String aReason )
  {
    setStatus( "Analysis aborted! " + aReason );

    updateActions();
  }

  /**
   * @see nl.lxtreme.ols.api.tools.AnalysisCallback#analysisComplete(nl.lxtreme.ols.api.data.CapturedData)
   */
  @Override
  public void analysisComplete( final CapturedData aNewCapturedData )
  {
    if ( aNewCapturedData != null )
    {
      this.dataContainer.setCapturedData( aNewCapturedData );
    }
    if ( this.mainFrame != null )
    {
      repaintMainFrame();
    }

    setStatus( "" );
    updateActions();
  }

  /**
   * Cancels the current capturing (if in progress).
   */
  public void cancelCapture()
  {
    final DeviceController deviceController = getDeviceController();
    if ( deviceController == null )
    {
      return;
    }

    deviceController.cancel();
  }

  /**
   * @see nl.lxtreme.ols.api.devices.CaptureCallback#captureAborted(java.lang.String)
   */
  @Override
  public void captureAborted( final String aReason )
  {
    setStatus( "Capture aborted! " + aReason );
    updateActions();
  }

  /**
   * @see nl.lxtreme.ols.api.devices.CaptureCallback#captureComplete(nl.lxtreme.ols.api.data.CapturedData)
   */
  @Override
  public void captureComplete( final CapturedData aCapturedData )
  {
    setCapturedData( aCapturedData );

    setStatus( "Capture finished at {0,date,medium} {0,time,medium}.", new Date() );

    updateActions();
  }

  /**
   * Captures the data of the current device controller.
   * 
   * @param aParent
   *          the parent window to use, can be <code>null</code>.
   * @return <code>true</code> if the capture succeeded, <code>false</code>
   *         otherwise.
   * @throws IOException
   *           in case of I/O problems.
   */
  public boolean captureData( final Window aParent )
  {
    final DeviceController devCtrl = getDeviceController();
    if ( devCtrl == null )
    {
      return false;
    }

    try
    {
      if ( devCtrl.setupCapture( aParent ) )
      {
        setStatus( "Capture from {0} started at {1,date,medium} {1,time,medium} ...", devCtrl.getName(), new Date() );

        devCtrl.captureData( this );
        return true;
      }

      return false;
    }
    catch ( IOException exception )
    {
      captureAborted( "I/O problem: " + exception.getMessage() );

      // Make sure to handle IO-interrupted exceptions properly!
      if ( !HostUtils.handleInterruptedException( exception ) )
      {
        exception.printStackTrace();
      }

      return false;
    }
    finally
    {
      updateActions();
    }
  }

  /**
   * @see nl.lxtreme.ols.api.devices.CaptureCallback#captureStarted(int, int,
   *      int)
   */
  @Override
  public synchronized void captureStarted( final int aSampleRate, final int aChannelCount, final int aChannelMask )
  {
    final Runnable runner = new Runnable()
    {
      @Override
      public void run()
      {
        updateActions();
      }
    };

    if ( SwingUtilities.isEventDispatchThread() )
    {
      runner.run();
    }
    else
    {
      SwingUtilities.invokeLater( runner );
    }
  }

  /**
   * Clears all current cursors.
   */
  public void clearAllCursors()
  {
    for ( int i = 0; i < CapturedData.MAX_CURSORS; i++ )
    {
      this.dataContainer.setCursorPosition( i, null );
    }
    fireCursorChangedEvent( 0, -1 ); // removed...

    updateActions();
  }

  /**
   * Clears the current device controller.
   */
  public void clearDeviceController()
  {
    this.currentDevCtrl = null;
  }

  /**
   * Clears the current project, and start over as it were a new project, in
   * which no captured data is shown.
   */
  public void createNewProject()
  {
    this.projectManager.createNewProject();

    if ( this.mainFrame != null )
    {
      this.mainFrame.repaint();
    }

    updateActions();
  }

  /**
   * Exits the client application.
   */
  public void exit()
  {
    if ( this.host != null )
    {
      this.host.exit();
    }
  }

  /**
   * Exports the current diagram to the given exporter.
   * 
   * @param aExporter
   *          the exporter to export to, cannot be <code>null</code>.
   * @param aOutputStream
   *          the output stream to write the export to, cannot be
   *          <code>null</code>.
   * @throws IOException
   *           in case of I/O problems.
   */
  public void exportTo( final Exporter aExporter, final OutputStream aOutputStream ) throws IOException
  {
    if ( this.mainFrame != null )
    {
      aExporter.export( this.dataContainer, this.mainFrame.getDiagramScrollPane(), aOutputStream );
    }
  }

  /**
   * @see nl.lxtreme.ols.client.ActionProvider#getAction(java.lang.String)
   */
  public Action getAction( final String aID )
  {
    return this.actionManager.getAction( aID );
  }

  /**
   * @return the dataContainer
   */
  public DataContainer getDataContainer()
  {
    return this.dataContainer;
  }

  /**
   * Returns the current device controller.
   * 
   * @return the current device controller, can be <code>null</code>.
   */
  public DeviceController getDeviceController()
  {
    return this.currentDevCtrl;
  }

  /**
   * Returns all current tools known to the OSGi framework.
   * 
   * @return a collection of tools, never <code>null</code>.
   */
  public final Collection<DeviceController> getDevices()
  {
    final List<DeviceController> tools = new ArrayList<DeviceController>();
    synchronized ( this.bundleContext )
    {
      try
      {
        final ServiceReference[] serviceRefs = this.bundleContext.getAllServiceReferences(
            DeviceController.class.getName(), null );
        for ( ServiceReference serviceRef : serviceRefs )
        {
          tools.add( ( DeviceController )this.bundleContext.getService( serviceRef ) );
        }
      }
      catch ( InvalidSyntaxException exception )
      {
        throw new RuntimeException( exception );
      }
    }
    return tools;
  }

  /**
   * Returns the exporter with the given name.
   * 
   * @param aName
   *          the name of the exporter to return, cannot be <code>null</code>.
   * @return the exporter with the given name, can be <code>null</code> if no
   *         such exporter is found.
   * @throws IllegalArgumentException
   *           in case the given name was <code>null</code> or empty.
   */
  public Exporter getExporter( final String aName ) throws IllegalArgumentException
  {
    if ( ( aName == null ) || aName.trim().isEmpty() )
    {
      throw new IllegalArgumentException( "Name cannot be null or empty!" );
    }

    try
    {
      final ServiceReference[] serviceRefs = this.bundleContext
          .getAllServiceReferences( Exporter.class.getName(), null );
      final int count = ( serviceRefs == null ) ? 0 : serviceRefs.length;

      for ( int i = 0; i < count; i++ )
      {
        final Exporter exporter = ( Exporter )this.bundleContext.getService( serviceRefs[i] );

        if ( aName.equals( exporter.getName() ) )
        {
          return exporter;
        }
      }

      return null;
    }
    catch ( InvalidSyntaxException exception )
    {
      throw new RuntimeException( "getExporter failed!", exception );
    }
  }

  /**
   * Returns the names of all current available exporters.
   * 
   * @return an array of exporter names, never <code>null</code>, but can be
   *         empty.
   */
  public String[] getExporterNames()
  {
    try
    {
      final ServiceReference[] serviceRefs = this.bundleContext
          .getAllServiceReferences( Exporter.class.getName(), null );
      final int count = serviceRefs == null ? 0 : serviceRefs.length;

      final String[] result = new String[count];

      for ( int i = 0; i < count; i++ )
      {
        final Exporter exporter = ( Exporter )this.bundleContext.getService( serviceRefs[i] );

        result[i] = exporter.getName();
        this.bundleContext.ungetService( serviceRefs[i] );
      }

      return result;
    }
    catch ( InvalidSyntaxException exception )
    {
      throw new RuntimeException( "getAllExporterNames failed!", exception );
    }
  }

  /**
   * Returns the current project's filename.
   * 
   * @return a project filename, as file object, can be <code>null</code>.
   */
  public File getProjectFilename()
  {
    return this.projectManager.getCurrentProject().getFilename();
  }

  /**
   * Returns all current tools known to the OSGi framework.
   * 
   * @return a collection of tools, never <code>null</code>.
   */
  public final Collection<Tool> getTools()
  {
    final List<Tool> tools = new ArrayList<Tool>();
    synchronized ( this.bundleContext )
    {
      try
      {
        final ServiceReference[] serviceRefs = this.bundleContext.getAllServiceReferences( Tool.class.getName(), null );
        for ( ServiceReference serviceRef : serviceRefs )
        {
          tools.add( ( Tool )this.bundleContext.getService( serviceRef ) );
        }
      }
      catch ( InvalidSyntaxException exception )
      {
        throw new RuntimeException( exception );
      }
    }
    return tools;
  }

  /**
   * Goes to the current cursor position of the cursor with the given index.
   * 
   * @param aCursorIdx
   *          the index of the cursor to go to, >= 0 && < 10.
   */
  public void gotoCursorPosition( final int aCursorIdx )
  {
    if ( ( this.mainFrame != null ) && this.dataContainer.isCursorsEnabled() )
    {
      final Long cursorPosition = this.dataContainer.getCursorPosition( aCursorIdx );
      if ( cursorPosition != null )
      {
        this.mainFrame.gotoPosition( cursorPosition.longValue() );
      }
    }
  }

  /**
   * Goes to the current cursor position of the first available cursor.
   */
  public void gotoFirstAvailableCursor()
  {
    if ( ( this.mainFrame != null ) && this.dataContainer.isCursorsEnabled() )
    {
      for ( int c = 0; c < CapturedData.MAX_CURSORS; c++ )
      {
        if ( this.dataContainer.isCursorPositionSet( c ) )
        {
          final Long cursorPosition = this.dataContainer.getCursorPosition( c );
          if ( cursorPosition != null )
          {
            this.mainFrame.gotoPosition( cursorPosition.longValue() );
          }
          break;
        }
      }
    }
  }

  /**
   * Goes to the current cursor position of the last available cursor.
   */
  public void gotoLastAvailableCursor()
  {
    if ( ( this.mainFrame != null ) && this.dataContainer.isCursorsEnabled() )
    {
      for ( int c = CapturedData.MAX_CURSORS - 1; c >= 0; c-- )
      {
        if ( this.dataContainer.isCursorPositionSet( c ) )
        {
          final Long cursorPosition = this.dataContainer.getCursorPosition( c );
          if ( cursorPosition != null )
          {
            this.mainFrame.gotoPosition( cursorPosition.longValue() );
          }
          break;
        }
      }
    }
  }

  /**
   * Goes to the position of the trigger.
   */
  public void gotoTriggerPosition()
  {
    if ( ( this.mainFrame != null ) && this.dataContainer.hasTriggerData() )
    {
      final long position = this.dataContainer.getTriggerPosition();
      this.mainFrame.gotoPosition( position );
    }
  }

  /**
   * Returns whether there is a device selected or not.
   * 
   * @return <code>true</code> if there is a device selected, <code>false</code>
   *         if no device is selected.
   */
  public synchronized boolean isDeviceSelected()
  {
    return this.currentDevCtrl != null;
  }

  /**
   * Returns whether the current device is setup at least once.
   * 
   * @return <code>true</code> if the current device is setup,
   *         <code>false</code> otherwise.
   * @see #isDeviceSelected()
   */
  public synchronized boolean isDeviceSetup()
  {
    return ( this.currentDevCtrl != null ) && this.currentDevCtrl.isSetup();
  }

  /**
   * Returns whether or not the current project is changed.
   * 
   * @return <code>true</code> if the current project is changed,
   *         <code>false</code> if the current project is not changed.
   */
  public boolean isProjectChanged()
  {
    return this.projectManager.getCurrentProject().isChanged();
  }

  /**
   * Loads an OLS data file from the given file.
   * 
   * @param aFile
   *          the file to load as OLS data, cannot be <code>null</code>.
   * @throws IOException
   *           in case of I/O problems.
   */
  public void openDataFile( final File aFile ) throws IOException
  {
    final FileReader reader = new FileReader( aFile );

    try
    {
      final Project tempProject = this.projectManager.createTemporaryProject();
      OlsDataHelper.read( tempProject, reader );

      setChannelLabels( tempProject.getChannelLabels() );
      setCapturedData( tempProject.getCapturedData() );
      setCursorData( tempProject.getCursorPositions(), tempProject.isCursorsEnabled() );
    }
    finally
    {
      reader.close();

      zoomToFit();

      updateActions();
    }
  }

  /**
   * Opens the project denoted by the given file.
   * 
   * @param aFile
   *          the project file to open, cannot be <code>null</code>.
   * @throws IOException
   *           in case of I/O problems.
   */
  public void openProjectFile( final File aFile ) throws IOException
  {
    FileInputStream fis = new FileInputStream( aFile );

    this.projectManager.loadProject( fis );

    final Project project = this.projectManager.getCurrentProject();
    project.setFilename( aFile );

    zoomToFit();
  }

  /**
   * Removes the cursor denoted by the given cursor index.
   * 
   * @param aCursorIdx
   *          the index of the cursor to remove, >= 0 && < 10.
   */
  public void removeCursor( final int aCursorIdx )
  {
    if ( this.mainFrame != null )
    {
      this.dataContainer.setCursorPosition( aCursorIdx, null );
      fireCursorChangedEvent( aCursorIdx, -1 ); // removed...
    }

    updateActions();
  }

  /**
   * Removes a cursor change listener.
   * 
   * @param aListener
   *          the listener to remove, cannot be <code>null</code>.
   */
  public void removeCursorChangeListener( final DiagramCursorChangeListener aListener )
  {
    this.evenListeners.remove( DiagramCursorChangeListener.class, aListener );
  }

  /**
   * Removes the given device from the list of devices.
   * 
   * @param aDeviceController
   *          the device to remove, cannot be <code>null</code>.
   */
  public void removeDevice( final DeviceController aDeviceController )
  {
    if ( this.currentDevCtrl == aDeviceController )
    {
      this.currentDevCtrl = null;
    }

    if ( this.mainFrame != null )
    {
      this.mainFrame.removeDeviceMenuItem( aDeviceController.getName() );
    }

    updateActions();
  }

  /**
   * Removes the given exporter from the list of exporters.
   * 
   * @param aExporter
   *          the exporter to remove, cannot be <code>null</code>.
   */
  public void removeExporter( final Exporter aExporter )
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.removeExportMenuItem( aExporter.getName() );
    }

    updateActions();
  }

  /**
   * Removes the given tool from the list of tools.
   * 
   * @param aTool
   *          the tool to remove, cannot be <code>null</code>.
   */
  public void removeTool( final Tool aTool )
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.removeToolMenuItem( aTool.getName() );
    }

    updateActions();
  }

  /**
   * Repeats the capture with the current settings.
   * 
   * @param aParent
   *          the parent window to use, can be <code>null</code>.
   */
  public boolean repeatCaptureData( final Window aParent )
  {
    final DeviceController devCtrl = getDeviceController();
    if ( devCtrl == null )
    {
      return false;
    }

    try
    {
      setStatus( "Capture from {0} started at {1,date,medium} {1,time,medium} ...", devCtrl.getName(), new Date() );

      devCtrl.captureData( this );

      return true;
    }
    catch ( IOException exception )
    {
      captureAborted( "I/O problem: " + exception.getMessage() );

      exception.printStackTrace();

      // Make sure to handle IO-interrupted exceptions properly!
      HostUtils.handleInterruptedException( exception );

      return false;
    }
    finally
    {
      updateActions();
    }
  }

  /**
   * Runs the tool denoted by the given name.
   * 
   * @param aToolName
   *          the name of the tool to run, cannot be <code>null</code>;
   * @param aParent
   *          the parent window to use, can be <code>null</code>.
   */
  public void runTool( final String aToolName, final Window aParent )
  {
    if ( LOG.isLoggable( Level.INFO ) )
    {
      LOG.log( Level.INFO, "Running tool: \"{0}\" ...", aToolName );
    }

    final Tool tool = findToolByName( aToolName );
    if ( tool == null )
    {
      JOptionPane.showMessageDialog( aParent, "No such tool found: " + aToolName, "Error ...",
          JOptionPane.ERROR_MESSAGE );
    }
    else
    {
      final ToolContext context = createToolContext();
      tool.process( aParent, this.dataContainer, context, this );
    }

    updateActions();
  }

  /**
   * @see nl.lxtreme.ols.api.devices.CaptureCallback#samplesCaptured(java.util.List)
   */
  @Override
  public void samplesCaptured( final List<Sample> aSamples )
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.sampleCaptured( aSamples );
    }
    updateActions();
  }

  /**
   * Saves an OLS data file to the given file.
   * 
   * @param aFile
   *          the file to save the OLS data to, cannot be <code>null</code>.
   * @throws IOException
   *           in case of I/O problems.
   */
  public void saveDataFile( final File aFile ) throws IOException
  {
    final FileWriter writer = new FileWriter( aFile );
    try
    {
      final Project tempProject = this.projectManager.createTemporaryProject();
      tempProject.setCapturedData( this.dataContainer );

      OlsDataHelper.write( tempProject, writer );
    }
    finally
    {
      writer.flush();
      writer.close();
    }
  }

  /**
   * Saves the current project to the given file.
   * 
   * @param aFile
   *          the file to save the project information to, cannot be
   *          <code>null</code>.
   * @throws IOException
   *           in case of I/O problems.
   */
  public void saveProjectFile( final String aName, final File aFile ) throws IOException
  {
    FileOutputStream out = null;
    try
    {
      final Project project = this.projectManager.getCurrentProject();
      project.setFilename( aFile );
      project.setName( aName );

      out = new FileOutputStream( aFile );
      this.projectManager.saveProject( out );
    }
    finally
    {
      HostUtils.closeResource( out );
    }
  }

  /**
   * Sets whether or not cursors are enabled.
   * 
   * @param aState
   *          <code>true</code> if the cursors should be enabled,
   *          <code>false</code> otherwise.
   */
  public void setCursorMode( final boolean aState )
  {
    this.dataContainer.setCursorEnabled( aState );
    // Reflect the change directly on the diagram...
    repaintMainFrame();

    updateActions();
  }

  /**
   * Sets the cursor position of the cursor with the given index.
   * 
   * @param aCursorIdx
   *          the index of the cursor to set, >= 0 && < 10;
   * @param aLocation
   *          the mouse location on screen where the cursor should become,
   *          cannot be <code>null</code>.
   */
  public void setCursorPosition( final int aCursorIdx, final Point aLocation )
  {
    // Implicitly enable cursor mode, the user already had made its
    // intensions clear that he want to have this by opening up the
    // context menu anyway...
    setCursorMode( true );

    if ( this.mainFrame != null )
    {
      // Convert the mouse-position to a sample index...
      final long sampleIdx = this.mainFrame.convertMousePositionToSampleIndex( aLocation );

      this.dataContainer.setCursorPosition( aCursorIdx, Long.valueOf( sampleIdx ) );

      fireCursorChangedEvent( aCursorIdx, aLocation.x );
    }

    updateActions();
  }

  /**
   * Sets the current device controller to the given value.
   * 
   * @param aDeviceName
   *          the name of the device controller to set, cannot be
   *          <code>null</code>.
   */
  public synchronized void setDeviceController( final String aDeviceName )
  {
    if ( LOG.isLoggable( Level.INFO ) )
    {
      final String name = ( aDeviceName == null ) ? "no device" : aDeviceName;
      LOG.log( Level.INFO, "Setting current device controller to: \"{0}\" ...", name );
    }

    final Collection<DeviceController> devices = getDevices();
    for ( DeviceController device : devices )
    {
      if ( aDeviceName.equals( device.getName() ) )
      {
        this.currentDevCtrl = device;
      }
    }

    updateActions();
  }

  /**
   * @param aMainFrame
   *          the mainFrame to set
   */
  public void setMainFrame( final MainFrame aMainFrame )
  {
    if ( this.mainFrame != null )
    {
      this.projectManager.removePropertyChangeListener( this.mainFrame );
    }
    if ( aMainFrame != null )
    {
      this.projectManager.addPropertyChangeListener( aMainFrame );
    }

    this.mainFrame = aMainFrame;
  }

  /**
   * Sets a status message.
   * 
   * @param aMessage
   *          the message to set;
   * @param aMessageArgs
   *          the (optional) message arguments.
   */
  public final void setStatus( final String aMessage, final Object... aMessageArgs )
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.setStatus( aMessage, aMessageArgs );
    }
  }

  /**
   * Shows the "about OLS" dialog on screen. the parent window to use, can be
   * <code>null</code>.
   */
  public void showAboutBox()
  {
    MainFrame.showAboutBox( this.host.getVersion() );
  }

  /**
   * Shows a dialog with all running OSGi bundles.
   * 
   * @param aOwner
   *          the owning window to use, can be <code>null</code>.
   */
  public void showBundlesDialog( final Window aOwner )
  {
    BundlesDialog dialog = new BundlesDialog( aOwner, this.bundleContext );
    if ( dialog.showDialog() )
    {
      dialog.dispose();
      dialog = null;
    }
  }

  /**
   * Shows the label-editor dialog on screen.
   * <p>
   * Display the diagram labels dialog. Will block until the dialog is closed
   * again.
   * </p>
   * 
   * @param aParent
   *          the parent window to use, can be <code>null</code>.
   */
  public void showLabelsDialog( final Window aParent )
  {
    if ( this.mainFrame != null )
    {
      DiagramLabelsDialog dialog = new DiagramLabelsDialog( aParent, this.dataContainer.getChannelLabels() );
      if ( dialog.showDialog() )
      {
        final String[] channelLabels = dialog.getChannelLabels();
        setChannelLabels( channelLabels );
      }

      dialog.dispose();
      dialog = null;
    }
  }

  /**
   * Shows the settings-editor dialog on screen.
   * <p>
   * Display the diagram settings dialog. Will block until the dialog is closed
   * again.
   * </p>
   * 
   * @param aParent
   *          the parent window to use, can be <code>null</code>.
   */
  public void showModeSettingsDialog( final Window aParent )
  {
    if ( this.mainFrame != null )
    {
      ModeSettingsDialog dialog = new ModeSettingsDialog( aParent, getDiagramSettings() );
      if ( dialog.showDialog() )
      {
        updateDiagramSettings( dialog.getDiagramSettings() );
      }

      dialog.dispose();
      dialog = null;
    }
  }

  /**
   * @param aOwner
   */
  public void showPreferencesDialog( final Window aParent )
  {
    GeneralSettingsDialog dialog = new GeneralSettingsDialog( aParent, getDiagramSettings() );
    if ( dialog.showDialog() )
    {
      updateDiagramSettings( dialog.getDiagramSettings() );
    }

    dialog.dispose();
    dialog = null;
  }

  /**
   * @see nl.lxtreme.ols.api.ProgressCallback#updateProgress(int)
   */
  @Override
  public void updateProgress( final int aPercentage )
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.setProgress( aPercentage );
    }
  }

  /**
   * Zooms in to the maximum zoom level.
   */
  public void zoomDefault()
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.zoomDefault();
    }

    updateActions();
  }

  /**
   * Zooms in with a factor of 2.0.
   */
  public void zoomIn()
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.zoomIn();
    }

    updateActions();
  }

  /**
   * Zooms out with a factor of 2.0.
   */
  public void zoomOut()
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.zoomOut();
    }

    updateActions();
  }

  /**
   * Zooms to fit the diagram to the current window dimensions.
   */
  public void zoomToFit()
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.zoomToFit();
    }

    updateActions();
  }

  /**
   * Returns the current main frame.
   * 
   * @return the main frame, can be <code>null</code>.
   */
  final MainFrame getMainFrame()
  {
    return this.mainFrame;
  }

  /**
   * Creates the tool context denoting the range of samples that should be
   * analysed by a tool.
   * 
   * @return a tool context, never <code>null</code>.
   */
  private ToolContext createToolContext()
  {
    int startOfDecode = -1;
    int endOfDecode = -1;

    final int dataLength = this.dataContainer.getValues().length;
    if ( this.dataContainer.isCursorsEnabled() )
    {
      if ( this.dataContainer.isCursorPositionSet( 0 ) )
      {
        final Long cursor1 = this.dataContainer.getCursorPosition( 0 );
        startOfDecode = this.dataContainer.getSampleIndex( cursor1.longValue() ) - 1;
      }
      if ( this.dataContainer.isCursorPositionSet( 1 ) )
      {
        final Long cursor2 = this.dataContainer.getCursorPosition( 1 );
        endOfDecode = this.dataContainer.getSampleIndex( cursor2.longValue() ) + 1;
      }
    }
    else
    {
      startOfDecode = 0;
      endOfDecode = dataLength;
    }

    startOfDecode = Math.max( 0, startOfDecode );
    if ( ( endOfDecode < 0 ) || ( endOfDecode >= dataLength ) )
    {
      endOfDecode = dataLength - 1;
    }

    return new DefaultToolContext( startOfDecode, endOfDecode );
  }

  /**
   * @param aActionManager
   */
  private void fillActionManager( final ActionManager aActionManager )
  {
    aActionManager.add( new NewProjectAction( this ) );
    aActionManager.add( new OpenProjectAction( this ) );
    aActionManager.add( new SaveProjectAction( this ) ).setEnabled( false );
    aActionManager.add( new SaveProjectAsAction( this ) ).setEnabled( false );
    aActionManager.add( new OpenDataFileAction( this ) );
    aActionManager.add( new SaveDataFileAction( this ) ).setEnabled( false );
    aActionManager.add( new ExitAction( this ) );

    aActionManager.add( new CaptureAction( this ) );
    aActionManager.add( new CancelCaptureAction( this ) ).setEnabled( false );
    aActionManager.add( new RepeatCaptureAction( this ) ).setEnabled( false );

    aActionManager.add( new ZoomInAction( this ) ).setEnabled( false );
    aActionManager.add( new ZoomOutAction( this ) ).setEnabled( false );
    aActionManager.add( new ZoomDefaultAction( this ) ).setEnabled( false );
    aActionManager.add( new ZoomFitAction( this ) ).setEnabled( false );

    aActionManager.add( new GotoTriggerAction( this ) ).setEnabled( false );
    aActionManager.add( new GotoCursor1Action( this ) ).setEnabled( false );
    aActionManager.add( new GotoCursor2Action( this ) ).setEnabled( false );
    aActionManager.add( new GotoFirstCursorAction( this ) ).setEnabled( false );
    aActionManager.add( new GotoLastCursorAction( this ) ).setEnabled( false );
    aActionManager.add( new ClearCursors( this ) ).setEnabled( false );
    aActionManager.add( new SetCursorModeAction( this ) );
    for ( int c = 0; c < CapturedData.MAX_CURSORS; c++ )
    {
      aActionManager.add( new SetCursorAction( this, c ) );
    }

    aActionManager.add( new ShowGeneralSettingsAction( this ) );
    aActionManager.add( new ShowModeSettingsAction( this ) );
    aActionManager.add( new ShowDiagramLabelsAction( this ) );

    aActionManager.add( new HelpAboutAction( this ) );
    aActionManager.add( new ShowBundlesAction( this ) );
  }

  /**
   * Searches for the tool with the given name.
   * 
   * @param aToolName
   *          the name of the tool to search for, cannot be <code>null</code>.
   * @return the tool with the given name, can be <code>null</code> if no such
   *         tool can be found.
   */
  private Tool findToolByName( final String aToolName )
  {
    Tool tool = null;

    final Collection<Tool> tools = getTools();
    for ( Tool _tool : tools )
    {
      if ( aToolName.equals( _tool.getName() ) )
      {
        tool = _tool;
        break;
      }
    }
    return tool;
  }

  /**
   * @param aCursorIdx
   * @param aMouseXpos
   */
  private void fireCursorChangedEvent( final int aCursorIdx, final int aMouseXpos )
  {
    final DiagramCursorChangeListener[] listeners = this.evenListeners.getListeners( DiagramCursorChangeListener.class );
    for ( final DiagramCursorChangeListener listener : listeners )
    {
      if ( aMouseXpos >= 0 )
      {
        listener.cursorChanged( aCursorIdx, aMouseXpos );
      }
      else
      {
        listener.cursorRemoved( aCursorIdx );
      }
    }
  }

  /**
   * Returns the current diagram settings.
   * 
   * @return the current diagram settings, can be <code>null</code> if there is
   *         no main frame to take the settings from.
   */
  private DiagramSettings getDiagramSettings()
  {
    return this.mainFrame != null ? this.mainFrame.getDiagramSettings() : null;
  }

  /**
   * Dispatches a request to repaint the entire main frame.
   */
  private void repaintMainFrame()
  {
    SwingUtilities.invokeLater( new Runnable()
    {
      @Override
      public void run()
      {
        ClientController.this.mainFrame.repaint();
      }
    } );
  }

  /**
   * Sets the captured data and zooms the view to show all the data.
   * 
   * @param aCapturedData
   *          the new captured data to set, cannot be <code>null</code>.
   */
  private void setCapturedData( final CapturedData aCapturedData )
  {
    this.dataContainer.setCapturedData( aCapturedData );

    if ( this.mainFrame != null )
    {
      this.mainFrame.zoomToFit();
    }
  }

  /**
   * Set the channel labels.
   * 
   * @param aChannelLabels
   *          the channel labels to set, cannot be <code>null</code>.
   */
  private void setChannelLabels( final String[] aChannelLabels )
  {
    if ( aChannelLabels != null )
    {
      this.dataContainer.setChannelLabels( aChannelLabels );
      this.mainFrame.setChannelLabels( aChannelLabels );
    }
  }

  /**
   * @param aCursorData
   *          the cursor positions to set, cannot be <code>null</code>;
   * @param aCursorsEnabled
   *          <code>true</code> if cursors should be enabled, <code>false</code>
   *          if they should be disabled.
   */
  private void setCursorData( final Long[] aCursorData, final boolean aCursorsEnabled )
  {
    this.dataContainer.setCursorEnabled( aCursorsEnabled );
    for ( int i = 0; i < CapturedData.MAX_CURSORS; i++ )
    {
      this.dataContainer.setCursorPosition( i, aCursorData[i] );
    }
  }

  /**
   * Synchronizes the state of the actions to the current state of this host.
   */
  private void updateActions()
  {
    final DeviceController currentDeviceController = getDeviceController();

    final boolean deviceControllerSet = currentDeviceController != null;
    final boolean deviceCapturing = deviceControllerSet && currentDeviceController.isCapturing();
    final boolean deviceSetup = deviceControllerSet && !deviceCapturing && currentDeviceController.isSetup();

    getAction( CaptureAction.ID ).setEnabled( deviceControllerSet );
    getAction( CancelCaptureAction.ID ).setEnabled( deviceCapturing );
    getAction( RepeatCaptureAction.ID ).setEnabled( deviceSetup );

    final boolean projectChanged = this.projectManager.getCurrentProject().isChanged();
    final boolean projectSavedBefore = this.projectManager.getCurrentProject().getFilename() != null;
    final boolean dataAvailable = this.dataContainer.hasCapturedData();

    getAction( SaveProjectAction.ID ).setEnabled( projectChanged );
    getAction( SaveProjectAsAction.ID ).setEnabled( projectSavedBefore && projectChanged );
    getAction( SaveDataFileAction.ID ).setEnabled( dataAvailable );

    getAction( ZoomInAction.ID ).setEnabled( dataAvailable );
    getAction( ZoomOutAction.ID ).setEnabled( dataAvailable );
    getAction( ZoomDefaultAction.ID ).setEnabled( dataAvailable );
    getAction( ZoomFitAction.ID ).setEnabled( dataAvailable );

    final boolean triggerEnable = dataAvailable && this.dataContainer.hasTriggerData();
    getAction( GotoTriggerAction.ID ).setEnabled( triggerEnable );

    // Update the cursor actions accordingly...
    final boolean enableCursors = dataAvailable && this.dataContainer.isCursorsEnabled();

    getAction( GotoCursor1Action.ID ).setEnabled( enableCursors && this.dataContainer.isCursorPositionSet( 0 ) );
    getAction( GotoCursor2Action.ID ).setEnabled( enableCursors && this.dataContainer.isCursorPositionSet( 1 ) );

    getAction( GotoFirstCursorAction.ID ).setEnabled( enableCursors );
    getAction( GotoLastCursorAction.ID ).setEnabled( enableCursors );

    getAction( SetCursorModeAction.ID ).setEnabled( dataAvailable );
    getAction( SetCursorModeAction.ID ).putValue( Action.SELECTED_KEY,
        Boolean.valueOf( this.dataContainer.isCursorsEnabled() ) );

    boolean anyCursorSet = false;
    for ( int c = 0; c < CapturedData.MAX_CURSORS; c++ )
    {
      final boolean cursorPositionSet = this.dataContainer.isCursorPositionSet( c );
      anyCursorSet |= cursorPositionSet;

      final Action action = getAction( SetCursorAction.getCursorId( c ) );
      action.setEnabled( dataAvailable );
      action.putValue( Action.SELECTED_KEY, Boolean.valueOf( cursorPositionSet ) );
    }

    getAction( ClearCursors.ID ).setEnabled( enableCursors && anyCursorSet );
  }

  /**
   * Should be called after the diagram settings are changed. This method will
   * cause the settings to be set on the main frame and writes them to the
   * preference store.
   * 
   * @param aSettings
   *          the (new/changed) diagram settings to set, cannot be
   *          <code>null</code>.
   */
  private void updateDiagramSettings( final DiagramSettings aSettings )
  {
    if ( this.mainFrame != null )
    {
      this.mainFrame.setDiagramSettings( aSettings );
      repaintMainFrame();
    }
  }
}
