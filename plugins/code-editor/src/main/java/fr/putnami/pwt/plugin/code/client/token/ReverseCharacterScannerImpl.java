/**
 * This file is part of pwt.
 *
 * pwt is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * pwt is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with pwt. If not,
 * see <http://www.gnu.org/licenses/>.
 */
package fr.putnami.pwt.plugin.code.client.token;

import com.google.common.base.Preconditions;

public class ReverseCharacterScannerImpl implements CharacterScanner {

	private String toScan;
	private int offset;
	private int mark;
	private int rangeStart;
	private int rangeEnd;

	public ReverseCharacterScannerImpl() {
	}

	public ReverseCharacterScannerImpl(String toScan) {
		this.setStringToScan(toScan);
	}

	@Override
	public void setStringToScan(String toScan) {
		String strToScan = toScan;
		if (strToScan == null) {
			strToScan = "";
		}
		this.setStringToScan(strToScan, strToScan.length() - 1, -1);
	}

	@Override
	public void setStringToScan(String toScan, int rangeStart, int rangeEnd) {
		Preconditions.checkArgument(toScan != null, "String to scan can not be null.");
		Preconditions.checkArgument(rangeEnd >= -1, "End range must be greater than -1.");
		Preconditions.checkArgument(rangeStart >= rangeEnd, "Start range must be greater than end range.");
		Preconditions.checkArgument(rangeStart < toScan.length(), "Start range must be lower than string to scan length.");
		this.toScan = toScan;
		this.offset = rangeStart;
		this.mark = this.offset;
		this.rangeStart = rangeStart;
		this.rangeEnd = rangeEnd;
	}

	@Override
	public int read() {
		try {
			if (this.offset > this.rangeEnd) {
				return this.toScan.charAt(this.offset);
			}

			return CharacterScanner.EOF;
		} finally {
			--this.offset;
		}
	}

	@Override
	public void unread() {
		if (this.offset < this.rangeStart) {
			++this.offset;
		}
	}

	@Override
	public int getOffset() {
		return this.offset;
	}

	@Override
	public int getMark() {
		return this.mark;
	}

	@Override
	public void mark() {
		this.mark = this.offset;
	}

	@Override
	public void resetToMark() {
		if (this.mark <= this.rangeStart) {
			this.offset = this.mark;
		} else {
			this.offset = this.rangeStart;
		}
	}

}
