import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(const QuickTextBannerApp());
}

class QuickTextBannerApp extends StatelessWidget {
  const QuickTextBannerApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Quick Text Banner',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF0B6E4F)),
        useMaterial3: true,
      ),
      home: const HomePage(),
    );
  }
}

class CustomAction {
  CustomAction({required this.name, required this.prompt});

  final String name;
  final String prompt;
}

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  static const MethodChannel _channel = MethodChannel('quick_text_banner');

  final TextEditingController _inputController = TextEditingController();
  final TextEditingController _aiExtraPromptController = TextEditingController();
  final TextEditingController _customNameController = TextEditingController();
  final TextEditingController _customPromptController = TextEditingController();
  final List<String> _texts = <String>['Hello', 'Thank you', 'Please send details'];
  final List<CustomAction> _customActions = <CustomAction>[];

  bool _overlayOn = false;
  bool _toggleOn = false;
  bool _canOverlay = false;
  bool _accessibilityOn = false;
  bool _compactMode = false;
  int _rows = 2;
  String _platform = 'TikTok';
  bool _autoBannerEnabled = false;

  @override
  void initState() {
    super.initState();
    _loadLocalState();
    _refreshPermissions();
  }


  Future<void> _loadLocalState() async {
    final prefs = await SharedPreferences.getInstance();
    final texts = prefs.getStringList('quick_texts') ?? <String>[];
    final rows = prefs.getInt('rows') ?? 2;
    final compact = prefs.getBool('compact_mode') ?? false;
    final platform = prefs.getString('platform') ?? 'TikTok';
    final extraPrompt = prefs.getString('extra_prompt') ?? '';
    final customRaw = prefs.getStringList('custom_actions') ?? <String>[];
    final localAuto = prefs.getBool('auto_banner_enabled_local') ?? false;

    final parsed = customRaw.map((String line) {
      final parts = line.split('	');
      if (parts.length < 2) return null;
      return CustomAction(name: parts.first, prompt: parts.sublist(1).join('	'));
    }).whereType<CustomAction>().toList();

    if (!mounted) return;
    setState(() {
      if (texts.isNotEmpty) {
        _texts
          ..clear()
          ..addAll(texts);
      }
      _rows = rows.clamp(1, 4);
      _compactMode = compact;
      _platform = platform;
      _aiExtraPromptController.text = extraPrompt;
      _customActions
        ..clear()
        ..addAll(parsed);
      _autoBannerEnabled = localAuto;
    });
  }

  Future<void> _saveLocalState() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList('quick_texts', _texts);
    await prefs.setInt('rows', _rows);
    await prefs.setBool('compact_mode', _compactMode);
    await prefs.setString('platform', _platform);
    await prefs.setString('extra_prompt', _aiExtraPromptController.text.trim());
    await prefs.setBool('auto_banner_enabled_local', _autoBannerEnabled);
    await prefs.setStringList(
      'custom_actions',
      _customActions.map((CustomAction a) => '${a.name}	${a.prompt}').toList(),
    );
  }

  Future<void> _refreshPermissions() async {
    final bool canOverlay = await _channel.invokeMethod<bool>('canDrawOverlays') ?? false;
    final bool accessibility = await _channel.invokeMethod<bool>('isAccessibilityEnabled') ?? false;
    final bool autoBanner = await _channel.invokeMethod<bool>('getAutoBannerEnabled') ?? false;

    if (!mounted) return;
    setState(() {
      _canOverlay = canOverlay;
      _accessibilityOn = accessibility;
      _autoBannerEnabled = autoBanner;
    });
  }


  Future<void> _setAutoBannerEnabled(bool enabled) async {
    await _channel.invokeMethod('setAutoBannerEnabled', <String, dynamic>{
      'enabled': enabled,
    });
    if (!mounted) return;
    setState(() {
      _autoBannerEnabled = enabled;
    });
  }

  Future<void> _toggleBanner() async {
    if (_texts.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Add at least one text chip first.')),
      );
      return;
    }

    if (!_canOverlay) {
      await _channel.invokeMethod('openOverlaySettings');
      return;
    }

    if (!_accessibilityOn) {
      await _channel.invokeMethod('openAccessibilitySettings');
      return;
    }

    final bool actuallyVisible =
        await _channel.invokeMethod<bool>('isBannerVisible') ?? false;

    if (actuallyVisible) {
      await _channel.invokeMethod('hideOverlay');
    } else {
      await _channel.invokeMethod('showOverlay', <String, dynamic>{
        'texts': _texts,
        'rows': _rows,
        'compactMode': _compactMode,
        'userPrompt': _aiExtraPromptController.text.trim(),
        'platform': _platform,
        'customActions': _customActions
            .map((CustomAction a) => '${a.name}\t${a.prompt}')
            .toList(),
      });
    }

    if (!mounted) return;
    setState(() {
      _overlayOn = !actuallyVisible;
    });
  }


  Future<void> _toggleFloatingButton() async {
    if (!_canOverlay) {
      await _channel.invokeMethod('openOverlaySettings');
      return;
    }

    if (_toggleOn) {
      await _channel.invokeMethod('hideToggle');
    } else {
      await _channel.invokeMethod('showToggle');
    }

    if (!mounted) return;
    setState(() {
      _toggleOn = !_toggleOn;
    });
  }


  Future<void> _refreshBannerIfVisible() async {
    final bool visible =
        await _channel.invokeMethod<bool>('isBannerVisible') ?? false;
    if (!visible) return;

    await _channel.invokeMethod('showOverlay', <String, dynamic>{
      'texts': _texts,
      'rows': _rows,
      'compactMode': _compactMode,
      'userPrompt': _aiExtraPromptController.text.trim(),
      'platform': _platform,
      'customActions': _customActions
          .map((CustomAction a) => '${a.name}\t${a.prompt}')
          .toList(),
    });

    if (!mounted) return;
    setState(() {
      _overlayOn = true;
    });
  }

  void _addTextsFromInput() {
    final String raw = _inputController.text.trim();
    if (raw.isEmpty) return;

    final List<String> parts = raw
        .split(',')
        .map((String e) => e.trim())
        .where((String e) => e.isNotEmpty)
        .toList();

    if (parts.isEmpty) return;

    setState(() {
      _texts.addAll(parts);
      _inputController.clear();
      _saveLocalState();
    });
  }


  void _addCustomAction() {
    final String name = _customNameController.text.trim();
    final String prompt = _customPromptController.text.trim();
    if (name.isEmpty || prompt.isEmpty) return;
    setState(() {
      _customActions.add(CustomAction(name: name, prompt: prompt));
      _customNameController.clear();
      _customPromptController.clear();
      _saveLocalState();
    });
    _refreshBannerIfVisible();
  }

  void _removeCustomAction(int index) {
    setState(() {
      _customActions.removeAt(index);
      _saveLocalState();
    });
    _refreshBannerIfVisible();
  }

  void _removeAt(int index) {
    setState(() {
      _texts.removeAt(index);
      _saveLocalState();
    });
  }

  Future<void> _openLogsPage() async {
    if (!mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => const ApiLogsPage()),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Quick Text Banner'),
        actions: <Widget>[
          IconButton(
            onPressed: _openLogsPage,
            icon: const Icon(Icons.bug_report_outlined),
            tooltip: 'API Logs',
          ),
        ],
      ),
      body: SingleChildScrollView(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: <Widget>[
              Text('Status', style: Theme.of(context).textTheme.titleMedium),
              const SizedBox(height: 8),
              Text('Overlay permission: ${_canOverlay ? 'Enabled' : 'Missing'}'),
              Text('Accessibility service: ${_accessibilityOn ? 'Enabled' : 'Missing'}'),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: <Widget>[
                  OutlinedButton(
                    onPressed: () => _channel.invokeMethod('openOverlaySettings'),
                    child: const Text('Overlay Settings'),
                  ),
                  OutlinedButton(
                    onPressed: () => _channel.invokeMethod('openAccessibilitySettings'),
                    child: const Text('Accessibility Settings'),
                  ),
                  OutlinedButton(
                    onPressed: _refreshPermissions,
                    child: const Text('Refresh Status'),
                  ),
                ],
              ),
              const SizedBox(height: 8),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Auto open banner on input focus'),
                subtitle: const Text('Auto show on focus, auto hide on blur'),
                value: _autoBannerEnabled,
                onChanged: (bool value) {
                  _setAutoBannerEnabled(value);
                },
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _aiExtraPromptController,
                decoration: const InputDecoration(
                  labelText: 'Extra AI prompt (optional)',
                  hintText: 'e.g. how to make it?',
                ),
                onChanged: (_) => _saveLocalState(),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: _inputController,
                decoration: InputDecoration(
                  labelText: 'Add texts (comma-separated)',
                  hintText: 'text1, text2, text3',
                  suffixIcon: IconButton(
                    onPressed: _addTextsFromInput,
                    icon: const Icon(Icons.add),
                  ),
                ),
                onSubmitted: (_) => _addTextsFromInput(),
              ),
              const SizedBox(height: 8),
              Row(
                children: <Widget>[
                  const Text('Rows:'),
                  const SizedBox(width: 8),
                  DropdownButton<int>(
                    value: _rows,
                    items: const <DropdownMenuItem<int>>[
                      DropdownMenuItem(value: 1, child: Text('1')),
                      DropdownMenuItem(value: 2, child: Text('2')),
                      DropdownMenuItem(value: 3, child: Text('3')),
                      DropdownMenuItem(value: 4, child: Text('4')),
                    ],
                    onChanged: (int? value) {
                      if (value == null) return;
                      setState(() {
                        _rows = value;
                        _saveLocalState();
                      });
                      _refreshBannerIfVisible();
                    },
                  ),
                  const SizedBox(width: 16),
                  const Text('Chip:'),
                  const SizedBox(width: 8),
                  DropdownButton<bool>(
                    value: _compactMode,
                    items: const <DropdownMenuItem<bool>>[
                      DropdownMenuItem(value: false, child: Text('Full')),
                      DropdownMenuItem(value: true, child: Text('30%')),
                    ],
                    onChanged: (bool? value) {
                      if (value == null) return;
                      setState(() {
                        _compactMode = value;
                        _saveLocalState();
                      });
                      _refreshBannerIfVisible();
                    },
                  ),
                  const SizedBox(width: 16),
                  const Text('App:'),
                  const SizedBox(width: 8),
                  DropdownButton<String>(
                    value: _platform,
                    items: const <DropdownMenuItem<String>>[
                      DropdownMenuItem(value: 'TikTok', child: Text('TikTok')),
                      DropdownMenuItem(value: 'Instagram', child: Text('Instagram')),
                      DropdownMenuItem(value: 'X', child: Text('X')),
                      DropdownMenuItem(value: 'LinkedIn', child: Text('LinkedIn')),
                    ],
                    onChanged: (String? value) {
                      if (value == null) return;
                      setState(() {
                        _platform = value;
                        _saveLocalState();
                      });
                      _refreshBannerIfVisible();
                    },
                  ),
                ],
              ),
              const SizedBox(height: 12),
              Text('Custom AI Buttons', style: Theme.of(context).textTheme.titleSmall),
              const SizedBox(height: 8),
              TextField(
                controller: _customNameController,
                decoration: const InputDecoration(
                  labelText: 'Button name',
                  hintText: 'e.g. Product CTA',
                ),
              ),
              const SizedBox(height: 8),
              TextField(
                controller: _customPromptController,
                decoration: const InputDecoration(
                  labelText: 'Button prompt',
                  hintText: 'e.g. complete draft to push my product benefit',
                ),
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton(
                  onPressed: _addCustomAction,
                  child: const Text('Add Custom Button'),
                ),
              ),
              ..._customActions.asMap().entries.map((entry) {
                final int index = entry.key;
                final CustomAction action = entry.value;
                return Card(
                  child: ListTile(
                    dense: true,
                    title: Text(action.name),
                    subtitle: Text(
                      action.prompt,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    trailing: IconButton(
                      onPressed: () => _removeCustomAction(index),
                      icon: const Icon(Icons.delete_outline),
                    ),
                  ),
                );
              }),
              const SizedBox(height: 6),
              const SizedBox(height: 6),
              ..._texts.asMap().entries.map((entry) {
                final int index = entry.key;
                final String text = entry.value;
                return Card(
                  child: ListTile(
                    dense: true,
                    title: Text(text),
                    trailing: IconButton(
                      onPressed: () => _removeAt(index),
                      icon: const Icon(Icons.delete_outline),
                    ),
                  ),
                );
              }),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: _toggleBanner,
                  child: Text(_overlayOn ? 'Hide Banner' : 'Show Banner'),
                ),
              ),
              const SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: OutlinedButton(
                  onPressed: _toggleFloatingButton,
                  child: Text(_toggleOn ? 'Hide Floating Button' : 'Show Floating Button'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class ApiLogsPage extends StatefulWidget {
  const ApiLogsPage({super.key});

  @override
  State<ApiLogsPage> createState() => _ApiLogsPageState();
}

class _ApiLogsPageState extends State<ApiLogsPage> {
  static const MethodChannel _channel = MethodChannel('quick_text_banner');
  List<Map<String, dynamic>> _logs = <Map<String, dynamic>>[];
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _loadLogs();
  }

  Future<void> _loadLogs() async {
    setState(() => _loading = true);
    final String raw = await _channel.invokeMethod<String>('getApiLogs') ?? '[]';
    final List<dynamic> parsed = jsonDecode(raw) as List<dynamic>;
    setState(() {
      _logs = parsed.map((dynamic e) => Map<String, dynamic>.from(e as Map)).toList().reversed.toList();
      _loading = false;
    });
  }

  Future<void> _clearLogs() async {
    await _channel.invokeMethod('clearApiLogs');
    await _loadLogs();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('API Logs'),
        actions: <Widget>[
          IconButton(onPressed: _loadLogs, icon: const Icon(Icons.refresh)),
          IconButton(onPressed: _clearLogs, icon: const Icon(Icons.delete_outline)),
        ],
      ),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _logs.isEmpty
              ? const Center(child: Text('No logs yet'))
              : ListView.builder(
                  itemCount: _logs.length,
                  itemBuilder: (BuildContext context, int index) {
                    final log = _logs[index];
                    return ExpansionTile(
                      title: Text('${log['time'] ?? '-'} | ${log['status'] ?? '-'}'),
                      subtitle: Text(
                        (log['instruction'] ?? '').toString(),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      childrenPadding: const EdgeInsets.all(12),
                      children: <Widget>[
                        SelectableText('visibleTextChars: ${log['visibleTextChars'] ?? 0}'),
                        const SizedBox(height: 8),
                        SelectableText('visibleTextSample:\n${log['visibleTextSample'] ?? ''}'),
                        const SizedBox(height: 8),
                        SelectableText('requestBody:\n${log['requestBody'] ?? ''}'),
                        const SizedBox(height: 8),
                        SelectableText('responseBody:\n${log['responseBody'] ?? ''}'),
                      ],
                    );
                  },
                ),
    );
  }
}
