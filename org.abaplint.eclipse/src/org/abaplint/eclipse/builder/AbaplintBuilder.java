package org.abaplint.eclipse.builder;

/*
 The MIT License (MIT)

 Copyright (c) 2016 Lars Hvam

 Permission is hereby granted, free of charge, to any person obtaining a copy
 of this software and associated documentation files (the "Software"), to deal
 in the Software without restriction, including without limitation the rights
 to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 copies of the Software, and to permit persons to whom the Software is
 furnished to do so, subject to the following conditions:

 The above copyright notice and this permission notice shall be included in all
 copies or substantial portions of the Software.

 THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 SOFTWARE.
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Scanner;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceDeltaVisitor;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

public class AbaplintBuilder extends IncrementalProjectBuilder {

	public static final String BUILDER_ID = "org.abaplint.eclipse.abaplintBuilder";
	private static final String MARKER_TYPE = "org.abaplint.eclipse.abaplintProblem";
	private static final String C_ABAPLINT = "AbapLint:";
	private static ScriptEngine engine = null;

	
	class SampleDeltaVisitor implements IResourceDeltaVisitor {

		public boolean visit(IResourceDelta delta) throws CoreException {
			IResource resource = delta.getResource();
			switch (delta.getKind()) {
			case IResourceDelta.ADDED:
				checkABAP(resource);
				break;
			case IResourceDelta.REMOVED:
				break;
			case IResourceDelta.CHANGED:
				checkABAP(resource);
				break;
		    default: // do nothing
		        break;				
			}
			return true;
		}
	}

	class SampleResourceVisitor implements IResourceVisitor {
		public boolean visit(IResource resource) {
			checkABAP(resource);
			// return true to continue visiting children.
			return true;
		}
	}

	private void addMarker(IFile file, String message, int lineNumber,
			int severity) {
		try {
			IMarker marker = file.createMarker(MARKER_TYPE);
			marker.setAttribute(IMarker.MESSAGE, message);
			marker.setAttribute(IMarker.SEVERITY, severity);
			marker.setAttribute(IMarker.LINE_NUMBER, lineNumber);
		} catch (CoreException e) {
		}
	}

	protected IProject[] build(int kind, Map<String, String> args,
			IProgressMonitor monitor) throws CoreException {
		if (kind == FULL_BUILD) {
			fullBuild(monitor);
		} else {
			IResourceDelta delta = getDelta(getProject());
			if (delta == null) {
				fullBuild(monitor);
			} else {
				incrementalBuild(delta, monitor);
			}
		}
		return null;
	}

	protected void clean(IProgressMonitor monitor) throws CoreException {
		getProject().deleteMarkers(MARKER_TYPE, true, IResource.DEPTH_INFINITE);
	}
	private static String convertStreamToString(java.io.InputStream is) {
		String contents="";
	    Scanner s=null;
	    try{
	    	s = new Scanner(is);
	    	s.useDelimiter("\\A");
  	    	contents = s.hasNext() ? s.next() : "";
	    }catch(Exception e) {
	    	e.printStackTrace(System.err);
	    }finally {
	    	if(s!=null)s.close();
	    }
	    return contents;
	}
	private String readJS() throws IOException {
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		InputStream input;
		input = classLoader.getResourceAsStream("/bundle/bundle.js");
		String bundle = convertStreamToString(input);
		return bundle;
	}

	private String readStream(InputStream in) throws IOException {
		StringBuilder sb = new StringBuilder();
		BufferedReader br = new BufferedReader(new InputStreamReader(in));
		String read = "";

		while ((read = br.readLine()) != null) {
			sb.append(read + "\n");
		}

		br.close();
		return sb.toString();
	}

	private ScriptEngine getEngine() throws IOException, ScriptException {
		if (engine != null) {
			return engine;
		}

		String script = readJS();

		ScriptEngineManager manager = new ScriptEngineManager();
		engine = manager.getEngineByName("JavaScript");

		engine.eval(script);
		engine.eval(
				"function run(name, contents) {\n" + 
				"  var file = new abaplint.File(name, contents);\n" + 
				"  var rules = [];\n" + 
				"  abaplint.Runner.run([file]).forEach(function(issue) {\n" + 
				"    rules.push({\n" + 
				"      description: issue.getRule().getDescription(),\n" + 
				"      start: issue.start.row\n" + 
				"    });\n" + 
				"  });\n" + 
				"  return rules;\n" + 
				"}");		

		return engine;
	}

	private void reportErrors(IFile file, Map<String,Object> results) {
		for(int i = 0;i<results.size();i++) {
			@SuppressWarnings("unchecked")
			Map<String,Object>  result = (Map<String,Object> ) results.get(String.valueOf(i));
			int start = ((Double)result.get("start")).intValue();
			addMarker(file,C_ABAPLINT+(String) result.get("description"), start,IMarker.SEVERITY_WARNING);
		}
	}

	private void runFile(IFile file) {
		try {
			Invocable inv = (Invocable) getEngine();
			String contents = readStream(file.getContents());
			@SuppressWarnings("unchecked")
			Map<String,Object>  lintResult = (Map<String,Object> ) inv.invokeFunction("run", "run_file",contents);	
			reportErrors(file, lintResult);
		} catch (NoSuchMethodException | ScriptException | CoreException
				| IOException e) {
			addMarker(file,C_ABAPLINT+ e.toString(), 1, IMarker.SEVERITY_WARNING);	
		}catch (IllegalStateException e) {
//			addMarker(file,C_ABAPLINT+ "Failed to read source (User not logged in?)", 1, IMarker.SEVERITY_INFO);			
		}
	}

	private void checkABAP(IResource resource) {
		if (resource instanceof IFile
				&& (resource.getName().endsWith(".asprog")
						|| resource.getName().endsWith(".aclass")
						|| resource.getName().endsWith(".acinc")
						|| resource.getName().endsWith(".asfugr") || resource
						.getName().endsWith(".aint"))) {
			IFile file = (IFile) resource;
			deleteMarkers(file);

			runFile(file);
		}
	}

	private void deleteMarkers(IFile file) {
		try {
			file.deleteMarkers(MARKER_TYPE, false, IResource.DEPTH_ZERO);
		} catch (CoreException ce) {
		}
	}

	protected void fullBuild(final IProgressMonitor monitor)
			throws CoreException {
		try {
			getProject().accept(new SampleResourceVisitor());
		} catch (CoreException e) {
		}
	}

	protected void incrementalBuild(IResourceDelta delta,
			IProgressMonitor monitor) throws CoreException {
		// the visitor does the work
		delta.accept(new SampleDeltaVisitor());
	}
}
