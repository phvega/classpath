/* GiopRmicCompiler -- Central GIOP-based RMI stub and tie compiler class.
   Copyright (C) 2006 Free Software Foundation

This file is part of GNU Classpath.

GNU Classpath is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2, or (at your option)
any later version.

GNU Classpath is distributed in the hope that it will be useful, but
WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
General Public License for more details.

You should have received a copy of the GNU General Public License
along with GNU Classpath; see the file COPYING.  If not, write to the
Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA
02110-1301 USA.

Linking this library statically or dynamically with other modules is
making a combined work based on this library.  Thus, the terms and
conditions of the GNU General Public License cover the whole
combination.

As a special exception, the copyright holders of this library give you
permission to link this library with independent modules to produce an
executable, regardless of the license terms of these independent
modules, and to copy and distribute the resulting executable under
terms of your choice, provided that you also meet, for each linked
independent module, the terms and conditions of the license of that
module.  An independent module is a module which is not derived from
or based on this library.  If you modify this library, you may extend
this exception to your version of the library, but you are not
obligated to do so.  If you do not wish to do so, delete this
exception statement from your version. */

package gnu.classpath.tools.giop.grmic;

import gnu.classpath.tools.AbstractMethodGenerator;

import java.lang.reflect.Method;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.TreeSet;

/**
 * Provides the extended rmic functionality to generate the POA - based classes
 * for GIOP (javax.rmi.CORBA package).
 * 
 * @author Audrius Meskauskas, Lithuania (audriusa@Bioinformatics.org)
 */
public class GiopRmicCompiler extends Generator
{
  /** The package name. */
  protected String packag;

  /**
   * The "basic" name (normally, the interface name, unless several Remote -
   * derived interfaces are implemented.
   */
  protected String name;

  /**
   * The name (without package) of the class, passed as the parameter.
   */
  protected String implName;
  
  /**
   * The proposed name for the stub.
   */
  protected String stubName;

  /**
   * The Remote's, implemented by this class.
   */
  protected Collection implementedRemotes = new HashSet();

  /**
   * The extra classes that must be imported.
   */
  protected Collection extraImports = new HashSet();

  /**
   * The methods we must implement.
   */
  protected Collection methods = new HashSet();

  /**
   * The map of all code generator variables.
   */
  public Properties vars = new Properties();
  
  /**
   * If this flag is set (true by default), the compiler generates the Servant
   * based classes. If set to false, the compiler generates the old style
   * ObjectImpl based classes.
   */
  protected boolean poaMode = true;
  
  /**
   * If this flag is set (true by default), the compiler emits warnings.
   */
  protected boolean warnings = true;
  
  /**
   * Verbose output
   */
  protected boolean verbose = false;

  /**
   * Clear data, preparing for the next compilation.
   */
  public void reset()
  {
    packag = name = implName = stubName = null;
    implementedRemotes.clear();
    extraImports.clear();
    methods.clear();
    vars.clear();
  }
  
  /**
   * Compile the given class (the instance of Remote), generating the stub and
   * tie for it.
   * 
   * @param remote
   *          the class to compile.
   */
  public synchronized void compile(Class remote)
  {
    reset();
    String s;

    // Get the package.
    s = remote.getName();
    int p = s.lastIndexOf('.');
    if (p < 0)
      {
        // Root package.
        packag = "";
        implName = name = s;
      }
    else
      {
        packag = s.substring(0, p);
        implName = name = s.substring(p + 1);
      }

    // Drop the Impl suffix, if one exists.
    if (name.endsWith("Impl"))
      name = name.substring(0, name.length() - "Impl".length());
    
    stubName = name;

    vars.put("#name", name);
    vars.put("#package", packag);
    vars.put("#implName", implName);
    
    if (verbose)
      System.out.println("Package " + packag + ", name " + name + " impl "
                       + implName);

    // Get the implemented remotes.
    Class[] interfaces = remote.getInterfaces();

    for (int i = 0; i < interfaces.length; i++)
      {
        if (Remote.class.isAssignableFrom(interfaces[i]))
          {
            if (!interfaces[i].equals(Remote.class))
              {
                implementedRemotes.add(interfaces[i]);
              }
          }
      }

    vars.put("#idList", getIdList(implementedRemotes));

    // Collect and process methods.
    Iterator iter = implementedRemotes.iterator();

    while (iter.hasNext())
      {
        Class c = (Class) iter.next();
        Method[] m = c.getMethods();

        // Check if throws RemoteException.
        for (int i = 0; i < m.length; i++)
          {
            Class[] exc = m[i].getExceptionTypes();
            boolean remEx = false;

            for (int j = 0; j < exc.length; j++)
              {
                if (RemoteException.class.isAssignableFrom(exc[j]))
                  {
                    remEx = true;
                    break;
                  }
                if (!remEx)
                  throw new CompilationError(m[i].getName() + ", defined in "
                                             + c.getName()
                                             + ", does not throw "
                                             + RemoteException.class.getName());
              }
            AbstractMethodGenerator mm = createMethodGenerator(m[i]);
            methods.add(mm);
          }
      }
  }
  
  /**
   * Create the method generator for the given method.
   * 
   * @param m the method
   * 
   * @return the created method generator
   */
  protected AbstractMethodGenerator createMethodGenerator(Method m)
  {
    return new MethodGenerator(m, this);
  }

  /**
   * Get the name of the given class. The class is added to imports, if not
   * already present and not from java.lang and not from the current package.
   * 
   * @param nameIt
   *          the class to name
   * @return the name of class as it should appear in java language
   */
  public String name(Class nameIt)
  {
    if (nameIt.isArray())
      {
        // Mesure dimensions:
        int dimension = 0;
        Class finalComponent = nameIt;
        while (finalComponent.isArray())
          {
            finalComponent = finalComponent.getComponentType();
            dimension++;
          }

        StringBuffer brackets = new StringBuffer();

        for (int i = 0; i < dimension; i++)
          {
            brackets.append("[]");
          }

        return name(finalComponent) + " " + brackets;
      }
    else
      {
        String n = nameIt.getName();
        if (!nameIt.isArray() && !nameIt.isPrimitive())
          if (!n.startsWith("java.lang")
              && !(packag != null && n.startsWith(packag)))
            extraImports.add(n);

        int p = n.lastIndexOf('.');
        if (p < 0)
          return n;
        else
          return n.substring(p + 1);
      }
  }

  /**
   * Get the RMI-style repository Id for the given class.
   * 
   * @param c
   *          the interface, for that the repository Id must be created.
   * @return the repository id
   */
  public String getId(Class c)
  {
    return "RMI:" + c.getName() + ":0000000000000000";
  }

  /**
   * Get repository Id string array declaration.
   * 
   * @param remotes
   *          the collection of interfaces
   * @return the fully formatted string array.
   */
  public String getIdList(Collection remotes)
  {
    StringBuffer b = new StringBuffer();

    // Keep the Ids sorted, ensuring, that the same order will be preserved
    // between compilations.
    TreeSet sortedIds = new TreeSet();

    Iterator iter = remotes.iterator();
    while (iter.hasNext())
      {
        sortedIds.add(getId((Class) iter.next()));
      }

    iter = sortedIds.iterator();
    while (iter.hasNext())
      {
        b.append("      \"" + iter.next() + "\"");
        if (iter.hasNext())
          b.append(", \n");
      }
    return b.toString();
  }

  /**
   * Generate stub. Can only be called from {@link #compile}.
   * 
   * @return the string, containing the text of the generated stub.
   */
  public String generateStub()
  {
    String template = getResource("Stub.jav");

    // Generate methods.
    StringBuffer b = new StringBuffer();
    Iterator iter = methods.iterator();
    while (iter.hasNext())
      {
        AbstractMethodGenerator m = (AbstractMethodGenerator) iter.next();
        b.append(m.generateStubMethod());
      }

    vars.put("#stub_methods", b.toString());
    vars.put("#imports", getImportStatements());
    vars.put("#interfaces", getAllInterfaces());

    String output = replaceAll(template, vars);
    return output;
  }
  
  /**
   * Get the list of all interfaces, implemented by the class, that are
   * derived from Remote.
   * 
   * @return the string - all interfaces.
   */
  public String getAllInterfaces()
  {
    StringBuffer b = new StringBuffer();
    Iterator iter = implementedRemotes.iterator();
    
    while (iter.hasNext())
      {
        b.append(name( (Class) iter.next() ) );
        if (iter.hasNext())
          b.append(", ");
      }
    
    return b.toString();
  }

  /**
   * Generate Tie. Can only be called from {@link #compile}.
   * 
   * @return the string, containing the text of the generated Tie.
   */
  public String generateTie()
  {
    String template;
    if (poaMode)
      template = getResource("Tie.jav");
    else
      template = getResource("ImplTie.jav");

    // Generate methods.
    StringBuffer b = new StringBuffer();
    Iterator iter = methods.iterator();
    while (iter.hasNext())
      {
        AbstractMethodGenerator m = (AbstractMethodGenerator) iter.next();
        b.append(m.generateTieMethod());
      }

    vars.put("#tie_methods", b.toString());

    vars.put("#imports", getImportStatements());

    String output = replaceAll(template, vars);
    return output;
  }

  
  /**
   * Import the extra classes, used as the method parameters and return values.
   * 
   * @return the additional import block.
   */
  protected String getImportStatements()
  {
    TreeSet imp = new TreeSet();

    Iterator it = extraImports.iterator();
    while (it.hasNext())
      {
        String ic = it.next().toString();
        imp.add("import " + ic + ";\n");
      }

    StringBuffer b = new StringBuffer();
    it = imp.iterator();

    while (it.hasNext())
      {
        b.append(it.next());
      }
    return b.toString();
  }
  
  /**
   * If this flag is set (true by default), the compiler generates the Servant
   * based classes. If set to false, the compiler generates the old style
   * ObjectImpl based classes.
   */
  public void setPoaMode(boolean mode)
  {
     poaMode = mode; 
  }
  
  /**
   * Set the verbose output mode (false by default)
   * 
   * @param isVerbose the verbose output mode
   */
  public void setVerbose(boolean isVerbose)
  {
    verbose = isVerbose;
  }
  
  /**
   * If this flag is set (true by default), the compiler emits warnings.
   */
  public void setWarnings(boolean warn)
  {
    warnings = warn;
  }
  
  /**
   * Get the package name.
   */
  public String getPackageName()
  {
    return packag;
  }
  
  /**
   * Get the proposed stub name
   */
  public String getStubName()
  {
    return stubName;
  }
}