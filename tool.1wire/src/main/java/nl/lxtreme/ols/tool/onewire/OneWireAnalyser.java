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
package nl.lxtreme.ols.tool.onewire;


import java.awt.*;
import java.beans.*;

import nl.lxtreme.ols.api.data.*;
import nl.lxtreme.ols.api.tools.*;
import nl.lxtreme.ols.tool.base.*;


/**
 * Provides a 1-wire analyser tool.
 */
public class OneWireAnalyser extends BaseAsyncTool<OneWireAnalyserDialog, OneWireDataSet, OneWireAnalyserWorker>
{
  // CONSTRUCTORS

  /**
   */
  public OneWireAnalyser()
  {
    super( Category.DECODER, "1-Wire protocol analyser ..." );
  }

  // METHODS

  /**
   * @see nl.lxtreme.ols.tool.base.BaseTool#createDialog(java.awt.Window,
   *      java.lang.String)
   */
  @Override
  protected OneWireAnalyserDialog createDialog( final Window aOwner, final String aName )
  {
    return new OneWireAnalyserDialog( aOwner, aName );
  }

  /**
   * @see nl.lxtreme.ols.tool.base.BaseAsyncTool#createToolWorker(nl.lxtreme.ols.api.data.DataContainer,
   *      nl.lxtreme.ols.api.tools.ToolContext)
   */
  @Override
  protected OneWireAnalyserWorker createToolWorker( final DataContainer aData, final ToolContext aContext )
  {
    return new OneWireAnalyserWorker( aData, aContext );
  }

  /**
   * @see nl.lxtreme.ols.tool.base.BaseAsyncTool#onPropertyChange(java.beans.PropertyChangeEvent)
   */
  @Override
  protected void onPropertyChange( final PropertyChangeEvent aEvent )
  {
    // NO-op
  }
}

/* EOF */
