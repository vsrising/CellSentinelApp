package com.asun.cellsentinelapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;

import com.google.android.material.chip.Chip;

import java.io.File;
import java.util.List;
import java.util.Locale;

public class SignalingLogFragment extends Fragment implements SignalingLogManager.Listener {

    private static SignalingLogManager sLogManager;

    private Button     mBtnToggle;
    private Button     mBtnClear;
    private Button     mBtnExport;
    private Button     mBtnAi;
    private ScrollView mScrollView;
    private TextView   mTvLog;
    private Chip       mChipAll, mChipHandover, mChipRat, mChipService;
    private Chip       mChipSimAll, mChipSim1, mChipSim2;

    private SignalingLogManager.EventType mFilter = null; // null = all

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View root = inflater.inflate(R.layout.fragment_signaling_log, container, false);

        mBtnToggle   = root.findViewById(R.id.btn_sig_toggle);
        mBtnClear    = root.findViewById(R.id.btn_sig_clear);
        mBtnExport   = root.findViewById(R.id.btn_sig_export);
        mBtnAi       = root.findViewById(R.id.btn_sig_ai);
        mScrollView  = root.findViewById(R.id.sv_sig_log);
        mTvLog       = root.findViewById(R.id.tv_sig_log);
        mChipAll     = root.findViewById(R.id.chip_filter_all);
        mChipHandover= root.findViewById(R.id.chip_filter_handover);
        mChipRat     = root.findViewById(R.id.chip_filter_rat);
        mChipService = root.findViewById(R.id.chip_filter_service);
        mChipSimAll  = root.findViewById(R.id.chip_sim_all);
        mChipSim1    = root.findViewById(R.id.chip_sim_1);
        mChipSim2    = root.findViewById(R.id.chip_sim_2);

        if (sLogManager == null)
            sLogManager = new SignalingLogManager(requireContext());

        mBtnToggle.setText(sLogManager.isRunning() ? "停止监听" : "开始监听");

        mBtnToggle.setOnClickListener(v -> {
            if (sLogManager.isRunning()) {
                sLogManager.stop();
                mBtnToggle.setText("开始监听");
            } else {
                sLogManager.start();
                mBtnToggle.setText("停止监听");
            }
        });

        mBtnClear.setOnClickListener(v -> {
            sLogManager.clearEvents();
            mTvLog.setText("");
        });

        mBtnExport.setOnClickListener(v -> exportCsv());
        mBtnAi.setOnClickListener(v -> startAiAnalysis());

        setupSimChips();
        setupFilterChips();
        rebuildLog();
        return root;
    }

    private void setupSimChips() {
        // Restore current selection
        int cur = sLogManager.getTargetSimIndex();
        mChipSimAll.setChecked(cur < 0);
        mChipSim1.setChecked(cur == 0);
        mChipSim2.setChecked(cur == 1);

        View.OnClickListener cl = v -> {
            mChipSimAll.setChecked(false);
            mChipSim1.setChecked(false);
            mChipSim2.setChecked(false);
            if (v == mChipSimAll) { mChipSimAll.setChecked(true); sLogManager.setTargetSimIndex(-1); }
            else if (v == mChipSim1) { mChipSim1.setChecked(true); sLogManager.setTargetSimIndex(0); }
            else { mChipSim2.setChecked(true); sLogManager.setTargetSimIndex(1); }
            rebuildLog();
        };
        mChipSimAll.setOnClickListener(cl);
        mChipSim1.setOnClickListener(cl);
        mChipSim2.setOnClickListener(cl);
    }

    private void setupFilterChips() {
        View.OnClickListener cl = v -> {
            mChipAll.setChecked(false);
            mChipHandover.setChecked(false);
            mChipRat.setChecked(false);
            mChipService.setChecked(false);
            if (v == mChipAll)      { mChipAll.setChecked(true);      mFilter = null; }
            else if (v == mChipHandover) { mChipHandover.setChecked(true); mFilter = SignalingLogManager.EventType.HANDOVER; }
            else if (v == mChipRat) { mChipRat.setChecked(true);      mFilter = SignalingLogManager.EventType.RAT_CHANGE; }
            else                    { mChipService.setChecked(true);   mFilter = SignalingLogManager.EventType.SERVICE_STATE; }
            rebuildLog();
        };
        mChipAll.setOnClickListener(cl);
        mChipHandover.setOnClickListener(cl);
        mChipRat.setOnClickListener(cl);
        mChipService.setOnClickListener(cl);
    }

    private void exportCsv() {
        if (sLogManager.getEvents().isEmpty()) {
            Toast.makeText(requireContext(), "暂无事件可导出", Toast.LENGTH_SHORT).show();
            return;
        }
        File f = sLogManager.exportCsv();
        if (f == null) {
            Toast.makeText(requireContext(), "导出失败", Toast.LENGTH_SHORT).show();
            return;
        }
        Toast.makeText(requireContext(), "已保存: " + f.getName(), Toast.LENGTH_LONG).show();
        try {
            Uri uri = FileProvider.getUriForFile(requireContext(),
                    requireContext().getPackageName() + ".provider", f);
            Intent share = new Intent(Intent.ACTION_SEND);
            share.setType("text/csv");
            share.putExtra(Intent.EXTRA_STREAM, uri);
            share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(share, "分享 CSV"));
        } catch (Exception ignored) {}
    }

    private void startAiAnalysis() {
        String ctx = buildSignalingContext();
        if (ctx == null) {
            Toast.makeText(requireContext(), "暂无信令事件可分析", Toast.LENGTH_SHORT).show();
            return;
        }
        AgentFragment.setPendingAnalysis(ctx, "signaling");
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new AgentFragment())
                .addToBackStack(null)
                .commit();
    }

    private String buildSignalingContext() {
        List<SignalingLogManager.SignalingEvent> events = sLogManager.getEvents();
        if (events.isEmpty()) return null;

        // ── Event type counters ──
        int cntHo = 0, cntRat = 0, cntSvc = 0, cntSig = 0;
        for (SignalingLogManager.SignalingEvent e : events) {
            switch (e.type) {
                case HANDOVER:      cntHo++;  break;
                case RAT_CHANGE:    cntRat++; break;
                case SERVICE_STATE: cntSvc++; break;
                case SIGNAL_CHANGE: cntSig++; break;
                default: break;
            }
        }

        // ── Signal quality distribution from all SIGNAL_CHANGE events ──
        int rsrpEx=0, rsrpGood=0, rsrpOk=0, rsrpBad=0, rsrpVBad=0;
        int sinrEx=0, sinrGood=0, sinrOk=0, sinrBad=0;
        int rsrpMin=Integer.MAX_VALUE, rsrpMax=Integer.MIN_VALUE; long rsrpSum=0; int rsrpCnt=0;
        int sinrMin=Integer.MAX_VALUE, sinrMax=Integer.MIN_VALUE; long sinrSum=0; int sinrCnt=0;
        int taMin=Integer.MAX_VALUE, taMax=0; int taCnt=0;
        for (SignalingLogManager.SignalingEvent e : events) {
            if (e.rsrp != Integer.MAX_VALUE) {
                rsrpCnt++; rsrpSum += e.rsrp;
                if (e.rsrp < rsrpMin) rsrpMin = e.rsrp;
                if (e.rsrp > rsrpMax) rsrpMax = e.rsrp;
                if      (e.rsrp >= -80)  rsrpEx++;
                else if (e.rsrp >= -90)  rsrpGood++;
                else if (e.rsrp >= -100) rsrpOk++;
                else if (e.rsrp >= -110) rsrpBad++;
                else                     rsrpVBad++;
            }
            if (e.sinr != Integer.MAX_VALUE) {
                sinrCnt++; sinrSum += e.sinr;
                if (e.sinr < sinrMin) sinrMin = e.sinr;
                if (e.sinr > sinrMax) sinrMax = e.sinr;
                if      (e.sinr >= 20) sinrEx++;
                else if (e.sinr >= 10) sinrGood++;
                else if (e.sinr >= 0)  sinrOk++;
                else                   sinrBad++;
            }
            if (e.ta != Integer.MAX_VALUE && e.ta >= 0) {
                taCnt++;
                if (e.ta < taMin) taMin = e.ta;
                if (e.ta > taMax) taMax = e.ta;
            }
        }

        // ── Duration ──
        long firstTs = events.get(0).timestamp;
        long lastTs  = events.get(events.size()-1).timestamp;
        long durSec  = (lastTs - firstTs) / 1000;
        String duration = String.format(Locale.US, "%02d:%02d:%02d",
                durSec/3600, (durSec%3600)/60, durSec%60);

        // ── Latest serving cell snapshot ──
        SignalingLogManager.SignalingEvent latest = events.get(events.size()-1);

        StringBuilder sb = new StringBuilder();
        sb.append("=== 专业信令事件分析数据 ===\n");
        sb.append("分析时间: ")
          .append(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                  .format(new java.util.Date())).append("\n");
        sb.append("设备: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL)
          .append("  Android ").append(android.os.Build.VERSION.RELEASE)
          .append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");

        // 一、测试概况
        sb.append("【一、测试概况】\n");
        sb.append("监听SIM: ").append(latest.simLabel.isEmpty() ? "全部" : latest.simLabel).append("\n");
        sb.append("监听时长: ").append(duration).append("\n");
        sb.append("事件总数: ").append(events.size())
          .append("  切换:").append(cntHo)
          .append("  RAT变更:").append(cntRat)
          .append("  服务状态:").append(cntSvc)
          .append("  信号变化:").append(cntSig).append("\n\n");

        // 二、当前服务小区快照
        sb.append("【二、当前服务小区快照】\n");
        sb.append("制式: ").append(latest.rat);
        if (latest.earfcn != Integer.MAX_VALUE) {
            String band = EarfcnDecoder.decodeLte(latest.earfcn);
            if (band == null && "NR".equals(latest.rat)) band = EarfcnDecoder.decodeNr(latest.earfcn);
            sb.append("  EARFCN:").append(latest.earfcn);
            if (band != null) sb.append(" (").append(band).append(")");
        }
        sb.append("\n");
        if (latest.ci != Integer.MAX_VALUE && latest.ci > 0) {
            sb.append("CI: ").append(latest.ci)
              .append("  eNB:").append(latest.enodebId)
              .append("  Cell:").append(latest.cellIndex).append("\n");
        }
        if (latest.pci != Integer.MAX_VALUE) sb.append("PCI: ").append(latest.pci).append("\n");
        if (latest.tac != Integer.MAX_VALUE) sb.append("TAC: ").append(latest.tac).append("\n");
        if (latest.rsrp != Integer.MAX_VALUE) {
            sb.append("RSRP: ").append(latest.rsrp).append(" dBm")
              .append("  [").append(rsrpLevel(latest.rsrp)).append("]\n");
        }
        if (latest.sinr != Integer.MAX_VALUE)
            sb.append("SINR: ").append(latest.sinr).append(" dB\n");
        if (latest.rsrq != Integer.MAX_VALUE)
            sb.append("RSRQ: ").append(latest.rsrq).append(" dB\n");
        if (latest.cqi != Integer.MAX_VALUE)
            sb.append("CQI: ").append(latest.cqi).append("  ").append(cqiDesc(latest.cqi)).append("\n");
        if (latest.bandwidth != Integer.MAX_VALUE)
            sb.append("带宽: ").append(latest.bandwidth / 1000).append(" MHz\n");
        if (latest.ta != Integer.MAX_VALUE && latest.ta >= 0) {
            sb.append("Timing Advance: ").append(latest.ta)
              .append("  →  估算距离: ").append((int)(latest.ta * 78.12)).append(" m\n");
        }
        // NR CSI
        if (latest.nr_rsrp != Integer.MAX_VALUE) {
            sb.append("NR SS-RSRP:").append(latest.nr_rsrp)
              .append("dBm  SS-SINR:").append(latest.nr_sinr != Integer.MAX_VALUE ? latest.nr_sinr : "N/A")
              .append("dB  SS-RSRQ:").append(latest.nr_rsrq != Integer.MAX_VALUE ? latest.nr_rsrq : "N/A").append("dB\n");
        }
        if (latest.nr_csirsrp != Integer.MAX_VALUE) {
            sb.append("NR CSI-RSRP:").append(latest.nr_csirsrp)
              .append("dBm  CSI-SINR:").append(latest.nr_csisinr != Integer.MAX_VALUE ? latest.nr_csisinr : "N/A")
              .append("dB\n");
        }
        if (!Double.isNaN(latest.latitude))
            sb.append("GPS: ").append(String.format(Locale.US, "%.6f, %.6f", latest.latitude, latest.longitude))
              .append(latest.altitude >= 0 ? "  海拔:" + (int)latest.altitude + "m" : "").append("\n");
        sb.append("\n");

        // 三、邻区列表
        if (!latest.neighbors.isEmpty()) {
            sb.append("【三、邻区列表 (").append(latest.neighbors.size()).append("个)】\n");
            int serRsrp = latest.rsrp != Integer.MAX_VALUE ? latest.rsrp
                        : latest.nr_rsrp != Integer.MAX_VALUE ? latest.nr_rsrp : Integer.MAX_VALUE;
            for (int i = 0; i < latest.neighbors.size(); i++) {
                CellSignalManager.NeighborCell nc = latest.neighbors.get(i);
                sb.append(String.format(Locale.US, "#%-2d ", i+1)).append(nc);
                if (serRsrp != Integer.MAX_VALUE && nc.rsrp != Integer.MAX_VALUE) {
                    int delta = nc.rsrp - serRsrp;
                    sb.append("  [ΔRSRP=").append(delta).append("dB");
                    if (delta >= -3) sb.append(" ⚠导频污染");
                    sb.append("]");
                }
                sb.append("\n");
                if (nc.earfcn != Integer.MAX_VALUE) {
                    String band = "LTE".equals(nc.rat) ? EarfcnDecoder.decodeLte(nc.earfcn)
                                                       : EarfcnDecoder.decodeNr(nc.earfcn);
                    if (band != null) sb.append("      → ").append(band).append("\n");
                }
            }
            sb.append("\n");
        }

        // 四、信号质量统计
        sb.append("【四、信号质量统计】\n");
        if (rsrpCnt > 0) {
            int avg = (int)(rsrpSum / rsrpCnt);
            sb.append("RSRP: 最小=").append(rsrpMin).append(" 均值=").append(avg)
              .append(" 最大=").append(rsrpMax).append(" dBm\n");
            sb.append(distBar("≥-80优秀",  rsrpEx,   rsrpCnt));
            sb.append(distBar("-80~-90良好",rsrpGood, rsrpCnt));
            sb.append(distBar("-90~-100可用",rsrpOk,  rsrpCnt));
            sb.append(distBar("-100~-110差", rsrpBad, rsrpCnt));
            sb.append(distBar("<-110极差",   rsrpVBad,rsrpCnt));
        }
        if (sinrCnt > 0) {
            int avg = (int)(sinrSum / sinrCnt);
            sb.append("SINR: 最小=").append(sinrMin).append(" 均值=").append(avg)
              .append(" 最大=").append(sinrMax).append(" dB\n");
            sb.append(distBar("≥20优秀",  sinrEx,   sinrCnt));
            sb.append(distBar("10~20良好",sinrGood, sinrCnt));
            sb.append(distBar("0~10可用",  sinrOk,  sinrCnt));
            sb.append(distBar("<0干扰",    sinrBad, sinrCnt));
        }
        if (taCnt > 0) {
            sb.append("TA范围: ").append(taMin).append("~").append(taMax)
              .append("  →  估算距离: ").append((int)(taMin*78.12)).append("m ~ ")
              .append((int)(taMax*78.12)).append("m\n");
        }
        sb.append("\n");

        // 五、切换事件详情
        if (cntHo > 0) {
            sb.append("【五、切换事件详情】\n");
            sb.append(String.format(Locale.US, "%-4s %-12s %-20s %-16s %-12s\n",
                    "序号","时间","切换信息","RSRP","GPS"));
            int hoNum = 0;
            for (SignalingLogManager.SignalingEvent e : events) {
                if (e.type != SignalingLogManager.EventType.HANDOVER) continue;
                hoNum++;
                String gps = !Double.isNaN(e.latitude)
                        ? String.format(Locale.US, "%.4f,%.4f", e.latitude, e.longitude) : "N/A";
                String rsrpStr = e.rsrp != Integer.MAX_VALUE ? e.rsrp + "dBm" : "N/A";
                String distStr = e.estimatedDistM >= 0 ? " (" + e.estimatedDistM + "m)" : "";
                sb.append(String.format(Locale.US, "#%-3d %s  %s  RSRP:%s%s  %s\n",
                        hoNum, e.formattedTime(), e.summary, rsrpStr, distStr, gps));
            }
            if (cntHo >= 2) {
                long minGap = Long.MAX_VALUE;
                long prevHoTs = -1;
                long gapSum = 0; int gapCnt = 0;
                for (SignalingLogManager.SignalingEvent e : events) {
                    if (e.type != SignalingLogManager.EventType.HANDOVER) continue;
                    if (prevHoTs > 0) {
                        long gap = (e.timestamp - prevHoTs) / 1000;
                        if (gap < minGap) minGap = gap;
                        gapSum += gap; gapCnt++;
                    }
                    prevHoTs = e.timestamp;
                }
                long avgGap = gapCnt > 0 ? gapSum / gapCnt : 0;
                sb.append("切换间隔: 均值=").append(avgGap).append("s  最短=").append(minGap).append("s");
                if (minGap < 15) sb.append("  ⚠频繁切换告警");
                sb.append("\n");
            }
            sb.append("\n");
        }

        // 六、信令事件时间序列
        sb.append("【六、信令事件时间序列 (最近60条)】\n");
        sb.append(String.format(Locale.US, "%-13s %-6s %-5s %-6s %-5s %-5s %-5s %-10s %-5s %-7s %-5s %s\n",
                "时间","类型","RAT","RSRP","SINR","RSRQ","PCI","CI","TA","距离m","速度","GPS"));
        int start = Math.max(0, events.size() - 60);
        for (int i = start; i < events.size(); i++) {
            SignalingLogManager.SignalingEvent e = events.get(i);
            String typeTag;
            switch (e.type) {
                case HANDOVER:      typeTag = "[HO ]"; break;
                case RAT_CHANGE:    typeTag = "[RAT]"; break;
                case SERVICE_STATE: typeTag = "[SVC]"; break;
                case SIGNAL_CHANGE: typeTag = "[SIG]"; break;
                default:            typeTag = "[---]"; break;
            }
            String gps = !Double.isNaN(e.latitude)
                    ? String.format(Locale.US, "%.4f,%.4f", e.latitude, e.longitude) : "";
            String spd = e.speed >= 0
                    ? String.format(Locale.US, "%.0fkm/h", e.speed * 3.6f) : "";
            sb.append(String.format(Locale.US, "%-13s %-6s %-5s %-6s %-5s %-5s %-5s %-10s %-5s %-7s %-5s %s\n",
                    e.formattedTime(), typeTag,
                    e.rat.equals("Unknown") ? "?" : e.rat,
                    e.rsrp != Integer.MAX_VALUE ? String.valueOf(e.rsrp) : "N/A",
                    e.sinr != Integer.MAX_VALUE ? String.valueOf(e.sinr) : "N/A",
                    e.rsrq != Integer.MAX_VALUE ? String.valueOf(e.rsrq) : "N/A",
                    e.pci  != Integer.MAX_VALUE ? String.valueOf(e.pci)  : "N/A",
                    e.ci   != Integer.MAX_VALUE && e.ci > 0 ? String.valueOf(e.ci) : "N/A",
                    e.ta   != Integer.MAX_VALUE ? String.valueOf(e.ta)   : "N/A",
                    e.estimatedDistM >= 0 ? String.valueOf(e.estimatedDistM) : "N/A",
                    spd, gps));
        }
        sb.append("\n");

        // 七、网络问题诊断提示
        sb.append("【七、网络诊断要点】\n");
        if (rsrpVBad > 0)
            sb.append("• 检测到弱覆盖事件 ").append(rsrpVBad).append(" 次 (RSRP<-110dBm)\n");
        if (sinrBad > 0)
            sb.append("• 检测到高干扰事件 ").append(sinrBad).append(" 次 (SINR<0dB)\n");
        boolean pilotPollution = false;
        for (SignalingLogManager.SignalingEvent e : events) {
            if (e.rsrp == Integer.MAX_VALUE || e.neighbors.isEmpty()) continue;
            for (CellSignalManager.NeighborCell nc : e.neighbors) {
                if (nc.rsrp != Integer.MAX_VALUE && (nc.rsrp - e.rsrp) >= -3) {
                    pilotPollution = true; break;
                }
            }
            if (pilotPollution) break;
        }
        if (pilotPollution)
            sb.append("• 检测到导频污染风险：邻区RSRP与服务小区差值≤3dB\n");
        if (cntRat > 0)
            sb.append("• RAT变更 ").append(cntRat).append(" 次，请检查LTE/NR覆盖连续性\n");
        sb.append("• 注：天线挂高/方位角/俯仰角/发射功率需从网管系统(如U2000/iManager)获取，设备端无法直接采集\n");
        sb.append("• 建议结合路测MR数据及网管告警进行综合分析\n");

        return sb.toString();
    }

    private static String rsrpLevel(int rsrp) {
        if (rsrp >= -80)  return "优秀";
        if (rsrp >= -90)  return "良好";
        if (rsrp >= -100) return "可用";
        if (rsrp >= -110) return "差";
        return "极差";
    }

    private static String cqiDesc(int cqi) {
        if (cqi >= 12) return "(64QAM高阶)";
        if (cqi >= 8)  return "(16QAM)";
        if (cqi >= 4)  return "(QPSK高)";
        if (cqi >= 1)  return "(QPSK低)";
        return "";
    }

    private static String distBar(String label, int count, int total) {
        int pct = total > 0 ? (int)(count * 100.0 / total) : 0;
        return String.format(Locale.US, "  %-12s %3d次 (%2d%%)\n", label, count, pct);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (sLogManager != null) {
            sLogManager.addListener(this);
            rebuildLog();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (sLogManager != null) sLogManager.removeListener(this);
    }

    @Override
    public void onNewEvent(SignalingLogManager.SignalingEvent event) {
        if (mFilter != null && event.type != mFilter) return;
        appendEvent(event);
        mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void rebuildLog() {
        if (sLogManager == null || mTvLog == null) return;
        List<SignalingLogManager.SignalingEvent> events = sLogManager.getEvents();
        StringBuilder sb = new StringBuilder();
        for (SignalingLogManager.SignalingEvent e : events) {
            if (mFilter != null && e.type != mFilter) continue;
            appendTo(sb, e);
        }
        mTvLog.setText(sb.length() == 0 ? "暂无事件" : sb.toString());
        mScrollView.post(() -> mScrollView.fullScroll(View.FOCUS_DOWN));
    }

    private void appendEvent(SignalingLogManager.SignalingEvent e) {
        if (mTvLog == null) return;
        StringBuilder sb = new StringBuilder(mTvLog.getText());
        if (sb.length() == 0 || "暂无事件".contentEquals(mTvLog.getText()))
            sb = new StringBuilder();
        appendTo(sb, e);
        mTvLog.setText(sb.toString());
    }

    private static void appendTo(StringBuilder sb, SignalingLogManager.SignalingEvent e) {
        String tag;
        switch (e.type) {
            case HANDOVER:      tag = "[HO ] "; break;
            case RAT_CHANGE:    tag = "[RAT] "; break;
            case SERVICE_STATE: tag = "[SVC] "; break;
            case SIGNAL_CHANGE: tag = "[SIG] "; break;
            default:            tag = "[--- ] "; break;
        }
        sb.append(e.formattedTime()).append("  ").append(tag).append(e.summary);
        if (!e.detail.isEmpty()) sb.append("\n             ").append(e.detail);
        sb.append("\n");
    }
}
