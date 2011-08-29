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

package com.adobe.epubcheck.ncx;

import java.io.IOException;

import com.adobe.epubcheck.api.Report;
import com.adobe.epubcheck.ocf.OCFPackage;
import com.adobe.epubcheck.opf.ContentChecker;
import com.adobe.epubcheck.opf.XRefChecker;
import com.adobe.epubcheck.xml.XMLParser;
import com.adobe.epubcheck.xml.XMLValidator;

public class NCXChecker implements ContentChecker {

	OCFPackage ocf;

	Report report;

	String path;

	XRefChecker xrefChecker;

	static XMLValidator ncxValidator = new XMLValidator("rng/ncx.rng");

	static XMLValidator ncxSchematronValidator = new XMLValidator("sch/ncx.sch");

	public NCXChecker(OCFPackage ocf, Report report, String path,
			XRefChecker xrefChecker) {
		this.ocf = ocf;
		this.report = report;
		this.path = path;
		this.xrefChecker = xrefChecker;
	}

	public void runChecks() {
		if (!ocf.hasEntry(path))
			report.error(null, 0, 0, "NCX file " + path + " is missing");
		else if (!ocf.canDecrypt(path))
			report.error(null, 0, 0, "NCX file " + path
					+ " cannot be decrypted");
		else {
			// relaxng
			XMLParser ncxParser = null;
			try {
				ncxParser = new XMLParser(ocf.getInputStream(path), path,
						report);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			ncxParser.addValidator(ncxValidator);
			NCXHandler ncxHandler = new NCXHandler(ncxParser, path, xrefChecker);
			ncxParser.addXMLHandler(ncxHandler);
			ncxParser.process();

			// schematron needs to go in a separate step, because of the catch
			// below
			// TODO: do it in a single step
			try {
				ncxParser = new XMLParser(ocf.getInputStream(path), path,
						report);
				ncxParser.addValidator(ncxSchematronValidator);
				ncxHandler = new NCXHandler(ncxParser, path, xrefChecker);
				ncxParser.process();
			} catch (Throwable t) {
				report.error(
						path,
						-1,
						0,
						"Failed performing NCX Schematron tests: "
								+ t.getMessage());
			}
		}
	}

}
