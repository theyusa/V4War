package io.nekohasekai.sagernet.aidl;

import io.nekohasekai.sagernet.aidl.ISagerNetServiceCallback;
import io.nekohasekai.sagernet.aidl.LiveStatsBatch;

interface ISagerNetService {
  int getState();
  String getProfileName();

  oneway void registerCallback(in ISagerNetServiceCallback cb, int id);
  oneway void unregisterCallback(in ISagerNetServiceCallback cb);
  oneway void resetTraffic(in long[] profileIds);

  // Starts an async URL test of the running instance; result is delivered via ISagerNetServiceCallback.cbUrlTestResult.
  oneway void startUrlTest(int requestId);

  // Refreshes the running instance's remote rule-sets; blocks up to about 60 s. Returns {"updated":n,"error":"text"}.
  String updateRuleSets();

  // The running auto-selector (bg.autoselector.AutoSelectorStatus as JSON; the member table only withMembers, which
  // cbAutoSelectorUpdate never carries); re-probe every running member; pin a member profile (-1 = automatic). Both
  // actions return at once and report through cbAutoSelectorUpdate.
  String autoSelectorStatus(boolean withMembers);
  void autoSelectorRecheck();
  void autoSelectorSelect(long memberProfileId);

  // The live graphs' points after afterSerial of session (bg.LiveStatsRecorder); another session returns them all.
  LiveStatsBatch liveStats(long session, long afterSerial);
}
