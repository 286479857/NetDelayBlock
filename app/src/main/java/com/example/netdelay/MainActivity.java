package com.example.netdelay;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.text.InputType;
import android.view.View;
import android.widget.*;

public class MainActivity extends Activity {
    private TextView status;
    private boolean busy;
    private int dp(int v) { return (int)(v * getResources().getDisplayMetrics().density + .5f); }
    public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(24),dp(32),dp(24),dp(24));
        layout.setBackgroundColor(Color.rgb(245,248,252)); scroll.addView(layout);
        TextView title = new TextView(this);
        title.setText("启动联网延迟 Root"); title.setTextSize(25); title.setTextColor(Color.rgb(30,52,78));
        layout.addView(title);
        TextView desc = new TextView(this);
        desc.setText("Root 防火墙控制应用联网。\n请先授权并检测，再测试目标应用。\n\n延迟秒数（0～300，0 表示关闭）");
        desc.setTextSize(16); desc.setPadding(0,dp(20),0,dp(12)); layout.addView(desc);
        final EditText seconds = new EditText(this);
        seconds.setInputType(InputType.TYPE_CLASS_NUMBER);
        seconds.setText(String.valueOf(getSharedPreferences("settings",0).getInt("seconds",5)));
        layout.addView(seconds);
        final CheckBox foreground = new CheckBox(this);
        foreground.setText("从后台重新打开时也重新计时");
        foreground.setChecked(getSharedPreferences("settings",0).getBoolean("foreground",false)); layout.addView(foreground);
        Button save = new Button(this); save.setText("保存设置"); layout.addView(save);
        save.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                try {
                    int value = Integer.parseInt(seconds.getText().toString().trim());
                    if (value < 0 || value > 300) throw new NumberFormatException();
                    boolean ok = getSharedPreferences("settings",0).edit().putInt("seconds",value)
                        .putBoolean("foreground",foreground.isChecked()).commit();
                    Toast.makeText(MainActivity.this,ok ? "已保存，请强行停止并重新打开目标应用" : "保存失败",Toast.LENGTH_LONG).show();
                    if (ok && value == 0) action("clear");
                } catch (NumberFormatException error) { seconds.setError("请输入 0～300 的整数"); }
            }
        });
        Button grant = new Button(this); grant.setText("授权 Root 并检测 / 启用"); layout.addView(grant);
        grant.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action("probe"); } });
        Button clear = new Button(this); clear.setText("停止启用，并解除全部拦截"); layout.addView(clear);
        clear.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action("clear"); } });
        Button counters = new Button(this); counters.setText("查看规则计数与清理日志"); layout.addView(counters);
        counters.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action("status"); } });
        status = new TextView(this); status.setTextSize(13); status.setTextIsSelectable(true);
        status.setPadding(0,dp(16),0,dp(16)); layout.addView(status); refresh();
        TextView help = new TextView(this);
        help.setText("操作步骤\n1. 点击授权 Root 并检测，选择永久允许。\n2. 看到 OK root + IPv4/IPv6 才表示防火墙可用。\n3. 在 LSPosed 启用模块，勾选目标应用，不要勾选系统框架。\n4. 保存秒数，强行停止目标应用，再打开测试。\n\n需要读取防火墙计数时，可在拦截窗口内回到此页面点查看。设置 30 秒更容易测试。\n\n覆盖与边界\n• 规则生效后，阻止该 UID 的 IPv4/IPv6 出站包，包含原生 TCP、UDP、QUIC 和已有连接继续发包。\n• 规则通过 Application.attach 触发；规则安装前的极早流量可能漏过。\n• WebView 隔离 UID、系统 DNS 代理、其他 UID 代发的流量可能绕过。\n• 共享 UID 的应用会一起受控；同 UID 的后台进程也受控。\n• 防火墙禁止发送，已到达的缓存/数据仍可能显示，不代表新请求成功。\n• 清理定时器正常情况下到时删除规则；异常时有第二个超时清理器。\n• 暂停期间请求可能超时，到时需应用自行重试或手动刷新。\n• 不支持 iptables、ip6tables 或 owner 匹配的内核会明确报告错误。\n• 本模块没有互联网权限。");
        help.setTextSize(14); layout.addView(help); setContentView(scroll);
    }
    public void onResume() { super.onResume(); if (status != null) refresh(); }
    private void refresh() {
        status.setText("Root 防火墙：" + (getSharedPreferences("settings",0).getBoolean("armed",false) ? "已启用" : "未启用")
            + "\n最近结果：\n" + getSharedPreferences("settings",0).getString("last_log","尚未检测"));
    }
    private void action(final String mode) {
        if (busy) { Toast.makeText(this,"操作进行中",Toast.LENGTH_SHORT).show(); return; }
        busy = true;
        if ("clear".equals(mode) || "probe".equals(mode))
            getSharedPreferences("settings",0).edit().putBoolean("armed",false).commit();
        status.setText("正在执行，请等待 Root 授权或检测结果…");
        new Thread(new Runnable() {
            public void run() {
                final String output = RootFirewall.run(MainActivity.this,mode,android.os.Process.myUid(),0);
                if ("probe".equals(mode)) getSharedPreferences("settings",0).edit()
                    .putBoolean("armed",output.startsWith("OK root")).commit();
                runOnUiThread(new Runnable() {
                    public void run() { busy = false; if (!isFinishing()) refresh(); }
                });
            }
        },"NetDelayRoot-UI").start();
    }
}
