/*
 * Copyright (c) 2007 Adobe Systems Incorporated
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy of
 *  this software and associated documentation files (the "Software"), to deal in
 *  the Software without restriction, including without limitation the rights to
 *  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *  the Software, and to permit persons to whom the Software is furnished to do so,
 *  subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.adobe.epubcheck.ops;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;

import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.opf.XRefChecker;
import com.adobe.epubcheck.util.PathUtil;
import com.adobe.epubcheck.xml.XMLElement;
import com.adobe.epubcheck.xml.XMLHandler;
import com.adobe.epubcheck.xml.XMLParser;

public class OPSHandler implements XMLHandler {

	String path;

	XRefChecker xrefChecker;

	static HashSet<String> regURISchemes = fillRegURISchemes();

	XMLParser parser;

	Report report;

	private static HashSet<String> fillRegURISchemes() {
		try {
			HashSet<String> set = new HashSet<String>();
			InputStream schemaStream = OPSHandler.class
					.getResourceAsStream("registeredSchemas.txt");
			BufferedReader schemaReader = new BufferedReader(
					new InputStreamReader(schemaStream));
			String schema = schemaReader.readLine();
			while (schema != null) {
				set.add(schema);
				schema = schemaReader.readLine();
			}
			schemaReader.close();
			schemaStream.close();
			return set;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public OPSHandler(String path, XRefChecker xrefChecker, XMLParser parser,
			Report report) {
		this.path = path;
		this.xrefChecker = xrefChecker;
		this.report = report;
		this.parser = parser;
	}

	private void checkPaint(XMLElement e, String attr) {
		String paint = e.getAttribute(attr);
		if (xrefChecker != null && paint != null && paint.startsWith("url(")
				&& paint.endsWith(")")) {
			String href = paint.substring(4, paint.length() - 1);
			href = PathUtil.resolveRelativeReference(path, href);
			xrefChecker.registerReference(path, parser.getLineNumber(),
					parser.getColumnNumber(), href, XRefChecker.RT_SVG_PAINT);
		}
	}

	private void checkClip(XMLElement e, String attr) {

	}

	private void checkImage(XMLElement e, String attrNS, String attr) {
		String href = e.getAttributeNS(attrNS, attr);
		if (xrefChecker != null && href != null) {
			href = PathUtil.resolveRelativeReference(path, href);
			xrefChecker.registerReference(path, parser.getLineNumber(),
					parser.getColumnNumber(), href, XRefChecker.RT_IMAGE);
		}
	}

	private void checkObject(XMLElement e, String attrNS, String attr) {
		String href = e.getAttributeNS(attrNS, attr);
		if (xrefChecker != null && href != null) {
			href = PathUtil.resolveRelativeReference(path, href);
			xrefChecker.registerReference(path, parser.getLineNumber(),
					parser.getColumnNumber(), href, XRefChecker.RT_OBJECT);
		}
	}

	private void checkLink(XMLElement e, String attrNS, String attr) {
		String href = e.getAttributeNS(attrNS, attr);
		String rel = e.getAttributeNS(attrNS, "rel");
		if (xrefChecker != null && href != null && rel != null
				&& rel.indexOf("stylesheet") >= 0) {
			href = PathUtil.resolveRelativeReference(path, href);
			xrefChecker.registerReference(path, parser.getLineNumber(),
					parser.getColumnNumber(), href, XRefChecker.RT_STYLESHEET);
		}
	}

	private void checkSymbol(XMLElement e, String attrNS, String attr) {
		String href = e.getAttributeNS(attrNS, attr);
		if (xrefChecker != null && href != null) {
			href = PathUtil.resolveRelativeReference(path, href);
			xrefChecker.registerReference(path, parser.getLineNumber(),
					parser.getColumnNumber(), href, XRefChecker.RT_SVG_SYMBOL);
		}
	}

	private void checkHRef(XMLElement e, String attrNS, String attr) {
		String href = e.getAttributeNS(attrNS, attr);
		if (href != null) {
			/*
			 * This section was replaced by the more broad and customizable
			 * isRegisteredSchemaType method, that checks to see if the href
			 * starts with one of the registered schema types read from the
			 * resource registeredSchemas.txt
			 * 
			 * if (href.startsWith("http://") || href.startsWith("https://") ||
			 * href.startsWith("ftp://") || href.startsWith("mailto:") ||
			 * href.startsWith("data:")) return;
			 */
			if (isRegisteredSchemaType(href))
				return;
			// This if statement is needed to make sure XML Fragment identifiers
			// are not reported as non-registered URI schema types
			else if (href.indexOf(':') > 0) {
				report.warning(path, parser.getLineNumber(),
						parser.getColumnNumber(),
						"use of non-registered URI schema type in href: "
								+ href);
				return;
			}
			try {
				href = PathUtil.resolveRelativeReference(path, href);
			} catch (IllegalArgumentException err) {
				report.error(path, parser.getLineNumber(),
						parser.getColumnNumber(), err.getMessage());
				return;
			}
			if (xrefChecker != null)
				xrefChecker.registerReference(path, parser.getLineNumber(),
						parser.getColumnNumber(), href,
						XRefChecker.RT_HYPERLINK);
		}
	}

	public static boolean isRegisteredSchemaType(String href) {
		int colonIndex = href.indexOf(':');
		if (colonIndex < 0)
			return false;
		else if (regURISchemes.contains(href.substring(0, colonIndex + 1)))
			return true;
		else if (href.length() > colonIndex + 2)
			if (href.substring(colonIndex + 1, colonIndex + 3).equals("//")
					&& regURISchemes
							.contains(href.substring(0, colonIndex + 3)))
				return true;
			else
				return false;
		else
			return false;
	}

	public void startElement() {

		XMLElement e = parser.getCurrentElement();
		String id = e.getAttribute("id");
		String ns = e.getNamespace();
		String name = e.getName();
		int resourceType = XRefChecker.RT_GENERIC;
		if (ns != null) {
			if (ns.equals("http://www.w3.org/2000/svg")) {
				if (name.equals("parser.getLineNumber()arGradient")
						|| name.equals("radialGradient")
						|| name.equals("pattern"))
					resourceType = XRefChecker.RT_SVG_PAINT;
				else if (name.equals("clipPath"))
					resourceType = XRefChecker.RT_SVG_CLIP_PATH;
				else if (name.equals("symbol"))
					resourceType = XRefChecker.RT_SVG_SYMBOL;
				else if (name.equals("a"))
					checkHRef(e, "http://www.w3.org/1999/xlink", "href");
				else if (name.equals("use"))
					checkSymbol(e, "http://www.w3.org/1999/xlink", "href");
				else if (name.equals("image"))
					checkImage(e, "http://www.w3.org/1999/xlink", "href");
				checkPaint(e, "fill");
				checkPaint(e, "stroke");
				checkClip(e, "clip");
			} else if (ns.equals("http://www.w3.org/1999/xhtml")) {
				if (name.equals("a"))
					checkHRef(e, null, "href");
				else if (name.equals("img"))
					checkImage(e, null, "src");
				else if (name.equals("object"))
					checkObject(e, null, "data");
				else if (name.equals("link"))
					checkLink(e, null, "href");
				resourceType = XRefChecker.RT_HYPERLINK;
			}
		}
		if (xrefChecker != null && id != null)
			xrefChecker.registerAnchor(path, parser.getLineNumber(),
					parser.getColumnNumber(), id, resourceType);
	}

	public void endElement() {
	}

	public void ignorableWhitespace(char[] chars, int arg1, int arg2) {
	}

	public void characters(char[] chars, int arg1, int arg2) {
	}

	public void processingInstruction(String arg0, String arg1) {
	}
}
