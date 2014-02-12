/**
 * Copyright (C) 2011 The Serval Project
 *
 * This file is part of Serval Software (http://www.servalproject.org)
 *
 * Serval Software is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package org.servalproject.servald;

import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.servalproject.ServalBatPhoneApplication;
import org.servalproject.rhizome.RhizomeManifest;
import org.servalproject.rhizome.RhizomeManifestParseException;
import org.servalproject.servaldna.AbstractJniResults;
import org.servalproject.servaldna.BundleId;
import org.servalproject.servaldna.FileHash;
import org.servalproject.servaldna.IJniResults;
import org.servalproject.servaldna.ServalDCommand;
import org.servalproject.servaldna.ServalDFailureException;
import org.servalproject.servaldna.ServalDInterfaceError;
import org.servalproject.servaldna.ServalDResult;
import org.servalproject.servaldna.SubscriberId;

import java.io.File;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Low-level class for invoking servald JNI command-line operations.
 *
 * @author Andrew Bettison <andrew@servalproject.com>
 */
public class ServalD
{

	public static final String TAG = "ServalD";
	private static long started = -1;
	static boolean log = false;

	private ServalD() {
	}

	/** Start the servald server process if it is not already running.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void serverStart(String execPath)
			throws ServalDFailureException, ServalDInterfaceError {
		ServalDResult result = ServalDCommand.command("start", "exec", execPath);
		result.failIfStatusError();
		started = System.currentTimeMillis();
		Log.i(ServalD.TAG, "server " + (result.status == 0 ? "started" : "already running") + ", pid=" + result.getFieldInt("pid"));
	}

	public static void serverStart() throws ServalDFailureException,
			ServalDInterfaceError {
		serverStart(ServalBatPhoneApplication.context.coretask.DATA_FILE_PATH
				+ "/bin/servald");
	}
	/** Stop the servald server process if it is running.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void serverStop() throws ServalDFailureException,
			ServalDInterfaceError {
		ServalDResult result = ServalDCommand.command("stop");
		started = -1;
		result.failIfStatusError();
		Log.i(ServalD.TAG, "server " + (result.status == 0 ? "stopped, pid=" + result.getFieldInt("pid") : "not running"));
	}

	/** Query the servald server process status.
	 *
	 * @return	True if the process is running
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static boolean serverIsRunning() throws ServalDFailureException, ServalDInterfaceError {
		ServalDResult result = ServalDCommand.command("status");
		result.failIfStatusError();
		return result.status == 0;
	}

	public static long uptime() {
		if (started == -1)
			return -1;
		return System.currentTimeMillis() - started;
	}

	/** The result of a lookup operation.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static class LookupResult extends ServalDResult {
		public final SubscriberId subscriberId;
		public final String did;
		public final String name;
		/** Copy constructor. */
		protected LookupResult(LookupResult orig) {
			super(orig);
			this.subscriberId = orig.subscriberId;
			this.did = orig.did;
			this.name = orig.name;
		}
		/** Unpack a result from a keyring add operation.
		*
		* @param result		The result object returned by the operation.
		*
		* @author Andrew Bettison <andrew@servalproject.com>
		*/
		protected LookupResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
			if (result.status == 0) {
				this.subscriberId = getFieldSubscriberId("sid");
				this.did = getFieldStringNonEmptyOrNull("did");
				this.name = getFieldStringNonEmptyOrNull("name");
			} else {
				this.subscriberId = null;
				this.did = null;
				this.name = null;
			}
		}
	}

	/** The result of a keyring add operation.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static class KeyringAddResult extends LookupResult {
		/** Copy constructor. */
		protected KeyringAddResult(KeyringAddResult orig) {
			super(orig);
		}
		/** Unpack a result from a keyring add operation.
		*
		* @param result		The result object returned by the operation.
		*
		* @author Andrew Bettison <andrew@servalproject.com>
		*/
		protected KeyringAddResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
		}
	}

	public static KeyringAddResult keyringAdd() throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = ServalDCommand.command("keyring", "add");
		result.failIfStatusError();
		return new KeyringAddResult(result);
	}

	public static KeyringAddResult keyringSetDidName(SubscriberId sid, String did, String name) throws ServalDFailureException, ServalDInterfaceError
	{
		List<String> args = new LinkedList<String>();
		args.add("keyring");
		args.add("set");
		args.add("did");
		args.add(sid.toHex());
		if (did != null)
			args.add(did);
		else if (name != null)
			args.add("");
		if (name != null)
			args.add(name);
		ServalDResult result = ServalDCommand.command(args.toArray(new String[args.size()]));
		result.failIfStatusError();
		return new KeyringAddResult(result);
	}

	/** The result of a keyring list.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static class KeyringListResult extends ServalDResult {
		static class Entry {
			public final SubscriberId subscriberId;
			public final String did;
			public final String name;
			protected Entry(SubscriberId sid, String did, String name) {
				this.subscriberId = sid;
				this.did = did;
				this.name = name;
			}
		}
		public final Entry[] entries;
		/** Copy constructor. */
		protected KeyringListResult(KeyringListResult orig) {
			super(orig);
			this.entries = orig.entries;
		}
		/** Unpack a result from a keyring list output.
		*
		* @param result		The result object returned by the operation.
		*
		* @author Andrew Bettison <andrew@servalproject.com>
		*/
		protected KeyringListResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
			if (this.outv.length % 3 != 0)
				throw new ServalDInterfaceError("invalid number of fields " + this.outv.length + " (not multiple of 3)", this);
			Entry[] entries = new Entry[this.outv.length / 3];
			for (int i = 0; i != this.outv.length; i += 3)
				try {
					entries[i / 3] = new Entry(
							new SubscriberId(new String(this.outv[i])),
							this.outv[i + 1].length != 0 ? new String(this.outv[i + 1]) : null,
							this.outv[i + 2].length != 0 ? new String(this.outv[i + 2]) : null
						);
				} catch (SubscriberId.InvalidHexException e) {
					throw new ServalDInterfaceError("invalid output field outv[" + i + "]", this, e);
				}
			this.entries = entries;
		}
	}

	public static KeyringListResult keyringList() throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = ServalDCommand.command("keyring", "list");
		result.failIfStatusError();
		return new KeyringListResult(result);
	}

	public static void dnaLookup(final LookupResults results, String did)
			throws ServalDFailureException, ServalDInterfaceError {
		dnaLookup(results, did, 3000);
	}

	public static void dnaLookup(final LookupResults results,
			String did, int timeout) throws ServalDFailureException,
			ServalDInterfaceError {
		if (log)
			Log.i(ServalD.TAG, "args = [dna, lookup, " + did + "]");
		int ret = ServalDCommand.command(new AbstractJniResults() {
			DnaResult nextResult;
			int resultNumber = 0;

			@Override
			public void putBlob(byte[] value) {
				String str = value == null ? "" : new String(value);
				if (log)
					Log.i(ServalD.TAG, "result = " + str);
				switch ((resultNumber++) % 3) {
					case 0:
						try {
							nextResult = new DnaResult(Uri.parse(str));
						} catch (Exception e) {
							Log.e(ServalD.TAG, "Unhandled dna response " + str, e);
							nextResult = null;
						}
						break;
					case 1:
						if (nextResult != null && nextResult.did == null)
							nextResult.did = str;
						break;
					case 2:
						if (nextResult != null) {
							nextResult.name = str;
							results.result(nextResult);
						}
						nextResult = null;
				}
			}
		}, new String[]{
				"dna", "lookup", did, Integer.toString(timeout)
		});

		if (ret == ServalDResult.STATUS_ERROR)
			throw new ServalDFailureException("error exit status");
	}

	/** The result of any rhizome operation that involves a payload.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	protected static class PayloadResult extends ServalDResult {

		public final FileHash fileHash;
		public final long fileSize;

		/** Copy constructor. */
		protected PayloadResult(PayloadResult orig) {
			super(orig);
			this.fileHash = orig.fileHash;
			this.fileSize = orig.fileSize;
		}

		/** Unpack a result from a rhizome operation that describes a payload file.
		*
		* @param result		The result object returned by the operation.
		*
		* @author Andrew Bettison <andrew@servalproject.com>
		*/
		protected PayloadResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
			this.fileSize = getFieldLong("filesize");
			this.fileHash = this.fileSize != 0 ? getFieldFileHash("filehash") : null;
		}

	}

	/**
	 * Add a payload file to the rhizome store, with author identity (SID).
	 *
	 * @param payloadPath	The path of the file containing the payload.  The name is taken from the
	 * 						path's basename.  If path is null, then it means an empty payload, and
	 * 						the name is empty also.
	 * @param manifestPath
	 * @param author 		The SID of the author or null.  If a SID is supplied, then bundle's
	 * 						secret key will be encoded into the manifest (in the BK field) using the
	 * 						author's rhizome secret, so that the author can update the file in
	 * 						future.  If no SID is provided, then the bundle carries no BK field, so
	 * 						the author will be unable to update the manifest with a new payload (ie,
	 * 						make a new version of the same bundle) unless she retains the bundle's
	 * 						secret key herself.
	 * @param pin 			The pin to unlock the author's rhizome secret.
	 * @return				PayloadResult
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeAddFileResult rhizomeAddFile(File payloadPath, File manifestPath, SubscriberId author, String pin)
		throws ServalDFailureException, ServalDInterfaceError
	{
		List<String> args = new LinkedList<String>();
		args.add("rhizome");
		args.add("add");
		args.add("file");
		if (pin != null) {
			args.add("--entry-pin");
			args.add(pin);
		}
		args.add(author == null ? "" : author.toHex());
		if (payloadPath != null)
			args.add(payloadPath.getAbsolutePath());
		else if (manifestPath != null)
			args.add("");
		if (manifestPath != null)
			args.add(manifestPath.getAbsolutePath());
		ServalDResult result = ServalDCommand.command(args.toArray(new String[args.size()]));
		if (result.status != 0 && result.status != 2)
			throw new ServalDFailureException("exit status indicates failure", result);
		return new RhizomeAddFileResult(result);
	}

	public static class RhizomeAddFileResult extends RhizomeManifestResult {
		RhizomeAddFileResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
		}
	}

	public static RhizomeManifestResult rhizomeImportBundle(File payloadFile,
			File manifestFile) throws ServalDFailureException,
			ServalDInterfaceError {
		ServalDResult result = ServalDCommand.command("rhizome", "import", "bundle",
				payloadFile.getAbsolutePath(), manifestFile.getAbsolutePath());
		result.failIfStatusError();
		RhizomeManifestResult ret = new RhizomeManifestResult(result);
		return ret;
	}

	public static Cursor rhizomeList(final String service, final String name, final SubscriberId sender, final SubscriberId recipient)
			throws ServalDFailureException, ServalDInterfaceError
	{
		return new ServalDCursor(){
			@Override
			void fillWindow(CursorWindowJniResults window, int offset, int numRows) throws ServalDFailureException{
				List<String> args = new LinkedList<String>();
				args.add("rhizome");
				args.add("list");
				args.add(service == null ? "" : service);
				args.add(name == null ? "" : name);
				args.add(sender == null ? "" : sender.toHex());
				args.add(recipient == null ? "" : recipient.toHex());
				if (offset > 0)
					args.add("" + offset);
				else if (numRows > 0)
					args.add("0");
				if (numRows > 0)
					args.add("" + numRows);
				int ret = ServalDCommand.command(window, args.toArray(new String[args.size()]));
				if (ret == ServalDResult.STATUS_ERROR)
					throw new ServalDFailureException("error exit status");
				if (ret != 0)
					throw new ServalDFailureException("non-zero exit status");
			}
		};
	}

	public static RhizomeExtractManifestResult rhizomeExtractBundle(
			BundleId manifestId, File manifestFile, File payloadFile)
			throws ServalDFailureException, ServalDInterfaceError {
		ServalDResult r = ServalDCommand.command("rhizome", "extract", "bundle",
				manifestId.toHex(),
				manifestFile == null ? "-" : manifestFile.getAbsolutePath(),
				payloadFile.getAbsolutePath());
		r.failIfStatusNonzero();
		RhizomeExtractManifestResult ret = new RhizomeExtractManifestResult(r);
		if (manifestFile == null && ret.manifest == null)
			throw new ServalDInterfaceError("missing manifest", ret);
		return ret;
	}

	/**
	 * Export a manifest into a file at the given path.
	 * 
	 * @param manifestId
	 *            The manifest ID of the manifest to extract.
	 * @param path
	 *            The path of the file into which the manifest is to be written.
	 * @return RhizomeExtractManifestResult
	 * 
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeExtractManifestResult rhizomeExportManifest(BundleId manifestId, File path) throws ServalDFailureException, ServalDInterfaceError
	{
		List<String> args = new LinkedList<String>();
		args.add("rhizome");
		args.add("export");
		args.add("manifest");
		args.add(manifestId.toString());
		if (path == null)
			args.add("-");
		else
			args.add(path.getAbsolutePath());
		ServalDResult result = ServalDCommand.command(args.toArray(new String[args.size()]));
		result.failIfStatusNonzero();
		RhizomeExtractManifestResult mresult = new RhizomeExtractManifestResult(result);
		if (path == null && mresult.manifest == null)
			throw new ServalDInterfaceError("missing manifest", mresult);
		return mresult;
	}

	public static class RhizomeManifestResult extends PayloadResult {
		public final String service;
		public final RhizomeManifest manifest;
		public final BundleId manifestId;
		public final long version;

		RhizomeManifestResult(ServalDResult result)
				throws ServalDInterfaceError {
			super(result);
			this.version = getFieldLong("version");
			this.service = getFieldString("service");
			this.manifestId = getFieldBundleId("manifestid");
			byte[] manifestBytes = getFieldByteArray("manifest", null);
			if (manifestBytes != null) {
				try {
					this.manifest = RhizomeManifest.fromByteArray(manifestBytes);
				}
				catch (RhizomeManifestParseException e) {
					throw new ServalDInterfaceError("invalid manifest", result, e);
				}
			}
			else
				this.manifest = null;
		}
	}

	public static class RhizomeExtractManifestResult extends
			RhizomeManifestResult {
		public final boolean _readOnly;
		public final SubscriberId _author;

		RhizomeExtractManifestResult(ServalDResult result)
				throws ServalDInterfaceError {
			super(result);
			this._readOnly = getFieldBoolean(".readonly");
			this._author = getFieldSubscriberId(".author", null);
		}
	}

	/**
	 * Extract a payload file into a file at the given path.
	 *
	 * @param bid		The id of the bundle to extract.
	 * @param path 		The path of the file into which the payload is to be written.
	 * @return			RhizomeExtractFileResult
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static RhizomeExtractFileResult rhizomeExtractFile(BundleId bid,
			File path) throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = ServalDCommand.command("rhizome", "extract", "file",
				bid.toHex(), path.getAbsolutePath());
		result.failIfStatusNonzero();
		return new RhizomeExtractFileResult(result);
	}

	/**
	 * Push Rhizome bundles to all configured direct hosts.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void rhizomeDirectPush() throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = ServalDCommand.command("rhizome", "direct", "push");
		result.failIfStatusNonzero();
	}

	/**
	 * Pull Rhizome bundles from all configured direct hosts.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void rhizomeDirectPull() throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = ServalDCommand.command("rhizome", "direct", "pull");
		result.failIfStatusNonzero();
	}

	/**
	 * Sync (push and pull) Rhizome bundles from all configured direct hosts.
	 *
	 * @author Andrew Bettison <andrew@servalproject.com>
	 */
	public static void rhizomeDirectSync() throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = ServalDCommand.command("rhizome", "direct", "sync");
		result.failIfStatusNonzero();
	}

	// copies the semantics of serval-dna's confParseBoolean
	private static boolean parseBoolean(String value, boolean defaultValue) {
		if (value == null || "".equals(value))
			return defaultValue;
		return "off".compareToIgnoreCase(value) != 0
				&& "no".compareToIgnoreCase(value) != 0
				&& "false".compareToIgnoreCase(value) != 0
				&& "0".compareToIgnoreCase(value) != 0;
	}

	public static class ConfigOption {
		final String var;
		final String value;
		private ConfigOption(String var, String value) {
			this.var = var;
			this.value = value;
		}
	}

	public static ConfigOption[] getConfigOptions(String pattern) {
		List<String> args = new LinkedList<String>();
		args.add("config");
		args.add("get");
		if (pattern != null)
			args.add(pattern);
		ServalDResult result = ServalDCommand.command(args.toArray(new String[args.size()]));
		Map<String,byte[]> vars = result.getKeyValueMap();
		List<ConfigOption> colist = new LinkedList<ConfigOption>();
		for (Map.Entry<String,byte[]> ent: vars.entrySet())
			colist.add(new ConfigOption(ent.getKey(), new String(ent.getValue())));
		return colist.toArray(new ConfigOption[0]);
	}

	public static String getConfig(String name) {
		String ret = null;
		ServalDResult result = ServalDCommand.command("config", "get", name);
		if (result.status == 0 && result.outv.length >= 2 && name.equals(new String(result.outv[0])))
			ret = new String(result.outv[1]);
		return ret;
	}

	public static void delConfig(String name) throws ServalDFailureException {
		ServalDResult result = ServalDCommand.command("config", "del", name);
		if (result.status != 2)
			result.failIfStatusNonzero();
	}

	public static void setConfig(String name, String value) throws ServalDFailureException {
		ServalDResult result = ServalDCommand.command("config", "set", name, value);
		if (result.status != 2)
			result.failIfStatusNonzero();
	}

	public static boolean getConfigBoolean(String name, boolean defaultValue) {
		String value = getConfig(name);
		return parseBoolean(value, defaultValue);
	}

	public static int getConfigInt(String name, int defaultValue) {
		String value = getConfig(name);
		return value == null ? defaultValue : Integer.parseInt(value);
	}

	public static boolean isRhizomeEnabled() {
		return getConfigBoolean("rhizome.enable", true);
	}

	public static class RhizomeExtractFileResult extends PayloadResult {
		RhizomeExtractFileResult(ServalDResult result) throws ServalDInterfaceError {
			super(result);
		}
	}

	public static int getPeerCount() throws ServalDFailureException {
		ServalDResult result = ServalDCommand.command("peer", "count");
		result.failIfStatusError();
		return Integer.parseInt(new String(result.outv[0]));
	}

	public static int peers(final IJniResults callback) throws ServalDInterfaceError
	{
		return ServalDCommand.command(callback, "id", "peers");
	}

	public static LookupResult reverseLookup(SubscriberId sid) throws ServalDFailureException, ServalDInterfaceError
	{
		ServalDResult result = ServalDCommand.command("reverse", "lookup", sid.toHex());
		result.failIfStatusError();
		return new LookupResult(result);
	}

	// MeshMS API
	public static Cursor listConversations(final SubscriberId sender)
			throws ServalDFailureException, ServalDInterfaceError
	{
		return new ServalDCursor() {
			@Override
			void fillWindow(CursorWindowJniResults window, int offset, int numRows) throws ServalDFailureException {
				int ret = ServalDCommand.command(window, "meshms", "list", "conversations",
						sender.toHex(), "" + offset, "" + numRows);
				if (ret!=0)
					throw new ServalDFailureException("Exit code "+ret);
			}
		};
	}

	public static Cursor listMessages(final SubscriberId sender, final SubscriberId recipient)
			throws ServalDFailureException, ServalDInterfaceError
	{
		return new ServalDCursor() {
			@Override
			void fillWindow(CursorWindowJniResults window, int offset, int numRows) throws ServalDFailureException {
				if (offset!=0 || numRows!=-1)
					throw new ServalDFailureException("Only one window supported");
				Log.v(TAG, "running meshms list messages "+sender+", "+recipient);
				int ret = ServalDCommand.command(window, "meshms", "list", "messages",
						sender.toHex(), recipient.toHex());
				if (ret!=0)
					throw new ServalDFailureException("Exit code "+ret);
			}
		};
	}

	public static void sendMessage(final SubscriberId sender, final SubscriberId recipient, String message) throws ServalDFailureException {
		ServalDResult ret = ServalDCommand.command("meshms", "send", "message",
				sender.toHex(), recipient.toHex(),
				message);
		ret.failIfStatusNonzero();
	}

	public static void readMessage(final SubscriberId sender, final SubscriberId recipient) throws ServalDFailureException {
		ServalDResult ret = ServalDCommand.command("meshms", "read", "messages",
				sender.toHex(), recipient.toHex());
		ret.failIfStatusNonzero();
	}

	public static void readMessage(final SubscriberId sender, final SubscriberId recipient, long offset) throws ServalDFailureException {
		ServalDResult ret = ServalDCommand.command("meshms", "read", "messages",
				sender.toHex(), recipient.toHex(),
				"" + offset);
		ret.failIfStatusNonzero();
	}
}
