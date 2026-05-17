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

class StaticCategory {
  StaticCategory({required this.name, required this.items});

  final String name;
  final List<String> items;
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
  final TextEditingController _categoryNameController = TextEditingController();
  final TextEditingController _categoryItemsController = TextEditingController();
  final TextEditingController _categoryBulkController = TextEditingController();
  final TextEditingController _categoryJsonController = TextEditingController();
  final List<CustomAction> _customActions = <CustomAction>[];
  final List<StaticCategory> _staticCategories = <StaticCategory>[];
  int _pageIndex = 0;

  bool _assistantOn = false;
  bool _canOverlay = false;
  bool _accessibilityOn = false;
  bool _compactMode = false;
  int _rows = 2;
  int _categoryRows = 1;
  bool _aiChipsEnabled = true;
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
    final rows = prefs.getInt('rows') ?? 2;
    final categoryRows = prefs.getInt('category_rows') ?? 1;
    final compact = prefs.getBool('compact_mode') ?? false;
    final aiChipsEnabled = prefs.getBool('ai_chips_enabled') ?? true;
    final platform = prefs.getString('platform') ?? 'TikTok';
    final extraPrompt = prefs.getString('extra_prompt') ?? '';
    final customRaw = prefs.getStringList('custom_actions') ?? <String>[];
    final categoriesRaw = prefs.getStringList('static_categories') ?? <String>[];
    final localAuto = prefs.getBool('auto_banner_enabled_local') ?? false;

    final parsed = customRaw.map((String line) {
      final parts = line.split('	');
      if (parts.length < 2) return null;
      return CustomAction(name: parts.first, prompt: parts.sublist(1).join('	'));
    }).whereType<CustomAction>().toList();
    final parsedCategories = categoriesRaw.map((String line) {
      final parts = line.split('\t');
      if (parts.length < 2) return null;
      final items = parts[1]
          .split('\u0001')
          .map((String e) => e.trim())
          .where((String e) => e.isNotEmpty)
          .toList();
      if (items.isEmpty || parts[0].trim().isEmpty) return null;
      return StaticCategory(name: parts[0].trim(), items: items);
    }).whereType<StaticCategory>().toList();

    if (!mounted) return;
    setState(() {
      _rows = rows.clamp(1, 4);
      _categoryRows = categoryRows.clamp(1, 3);
      _compactMode = compact;
      _aiChipsEnabled = aiChipsEnabled;
      _platform = platform;
      _aiExtraPromptController.text = extraPrompt;
      _customActions
        ..clear()
        ..addAll(parsed);
      _staticCategories
        ..clear()
        ..addAll(parsedCategories);
      _autoBannerEnabled = localAuto;
    });
  }

  Future<void> _saveLocalState() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt('rows', _rows);
    await prefs.setInt('category_rows', _categoryRows);
    await prefs.setBool('compact_mode', _compactMode);
    await prefs.setBool('ai_chips_enabled', _aiChipsEnabled);
    await prefs.setString('platform', _platform);
    await prefs.setString('extra_prompt', _aiExtraPromptController.text.trim());
    await prefs.setBool('auto_banner_enabled_local', _autoBannerEnabled);
    await prefs.setStringList(
      'custom_actions',
      _customActions.map((CustomAction a) => '${a.name}	${a.prompt}').toList(),
    );
    await prefs.setStringList(
      'static_categories',
      _staticCategories
          .map((StaticCategory c) => '${c.name}\t${c.items.join('\u0001')}')
          .toList(),
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

  Future<void> _setAssistantEnabled(bool enabled) async {
    if (enabled && _staticCategories.isEmpty) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Add at least one category first.')),
      );
      return;
    }

    if (enabled && !_canOverlay) {
      await _channel.invokeMethod('openOverlaySettings');
      return;
    }

    if (enabled && !_accessibilityOn) {
      await _channel.invokeMethod('openAccessibilitySettings');
      return;
    }

    if (enabled) {
      if (_autoBannerEnabled) {
        await _setAutoBannerEnabled(false);
      }
      await _channel.invokeMethod('showToggle');
      await _channel.invokeMethod('showOverlay', <String, dynamic>{
        'texts': <String>[],
        'rows': _rows,
        'compactMode': _compactMode,
        'userPrompt': _aiExtraPromptController.text.trim(),
        'platform': _platform,
        'aiChipsEnabled': _aiChipsEnabled,
        'categoryRows': _categoryRows,
        'customActions': _customActions
            .map((CustomAction a) => '${a.name}\t${a.prompt}')
            .toList(),
        'staticCategories': _staticCategories
            .map((StaticCategory c) => '${c.name}\t${c.items.join('\u0001')}')
            .toList(),
      });
    } else {
      await _channel.invokeMethod('hideOverlay');
      await _channel.invokeMethod('hideToggle');
    }

    if (!mounted) return;
    setState(() {
      _assistantOn = enabled;
    });
  }


  Future<void> _refreshBannerIfVisible() async {
    final bool visible =
        await _channel.invokeMethod<bool>('isBannerVisible') ?? false;
    if (!visible) return;

    await _channel.invokeMethod('showOverlay', <String, dynamic>{
      'texts': <String>[],
      'rows': _rows,
      'compactMode': _compactMode,
      'userPrompt': _aiExtraPromptController.text.trim(),
      'platform': _platform,
      'aiChipsEnabled': _aiChipsEnabled,
      'categoryRows': _categoryRows,
      'customActions': _customActions
          .map((CustomAction a) => '${a.name}\t${a.prompt}')
          .toList(),
      'staticCategories': _staticCategories
          .map((StaticCategory c) => '${c.name}\t${c.items.join('\u0001')}')
          .toList(),
    });

    if (!mounted) return;
    setState(() {
      _assistantOn = true;
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

  void _addStaticCategory() {
    final String name = _categoryNameController.text.trim();
    final List<String> items = _categoryItemsController.text
        .split(',')
        .map((String e) => e.trim())
        .where((String e) => e.isNotEmpty)
        .toList();
    if (name.isEmpty || items.isEmpty) return;
    setState(() {
      _staticCategories.add(StaticCategory(name: name, items: items));
      _categoryNameController.clear();
      _categoryItemsController.clear();
      _saveLocalState();
    });
    _refreshBannerIfVisible();
  }

  void _addStaticCategoriesFromBulk() {
    final String raw = _categoryBulkController.text.trim();
    if (raw.isEmpty) return;

    final List<String> tokens = raw
        .split(',')
        .map((String e) => e.trim())
        .where((String e) => e.isNotEmpty)
        .toList();
    if (tokens.isEmpty) return;

    final List<StaticCategory> parsed = <StaticCategory>[];
    String? currentName;
    final List<String> currentItems = <String>[];

    void flushCurrent() {
      if (currentName == null) return;
      final items = currentItems.where((String e) => e.isNotEmpty).toList();
      if (items.isNotEmpty) {
        parsed.add(StaticCategory(name: currentName!, items: items));
      }
    }

    for (final token in tokens) {
      final int colonIndex = token.indexOf(':');
      if (colonIndex > 0) {
        flushCurrent();
        currentItems.clear();
        currentName = token.substring(0, colonIndex).trim();
        final String firstItem = token.substring(colonIndex + 1).trim();
        if (firstItem.isNotEmpty) {
          currentItems.add(firstItem);
        }
      } else if (currentName != null) {
        currentItems.add(token);
      }
    }
    flushCurrent();

    if (parsed.isEmpty) return;
    setState(() {
      _staticCategories.addAll(parsed);
      _categoryBulkController.clear();
      _saveLocalState();
    });
    _refreshBannerIfVisible();
  }

  String _categoryJsonExample() {
    return jsonEncode(<Map<String, dynamic>>[
      <String, dynamic>{
        'name': 'Viral Reactions',
        'items': <String>['this edit is insane', 'clean transitions', 'deserved viral'],
      },
      <String, dynamic>{
        'name': 'Support',
        'items': <String>['keep going', 'you are improving fast'],
      },
    ]);
  }

  void _copyCategoryJsonExample() {
    Clipboard.setData(ClipboardData(text: _categoryJsonExample()));
    if (!mounted) return;
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('JSON example copied')),
    );
  }

  void _addStaticCategoriesFromJson() {
    final String raw = _categoryJsonController.text.trim();
    if (raw.isEmpty) return;

    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return;

      final List<StaticCategory> parsed = <StaticCategory>[];
      for (final item in decoded) {
        if (item is! Map) continue;
        final String name = (item['name'] ?? '').toString().trim();
        final dynamic itemsRaw = item['items'];
        if (name.isEmpty || itemsRaw is! List) continue;
        final List<String> items = itemsRaw
            .map((dynamic e) => e.toString().trim())
            .where((String e) => e.isNotEmpty)
            .toList();
        if (items.isEmpty) continue;
        parsed.add(StaticCategory(name: name, items: items));
      }

      if (parsed.isEmpty) return;
      setState(() {
        _staticCategories.addAll(parsed);
        _categoryJsonController.clear();
        _saveLocalState();
      });
      _refreshBannerIfVisible();
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Invalid JSON format')),
      );
    }
  }

  void _removeStaticCategory(int index) {
    setState(() {
      _staticCategories.removeAt(index);
      _saveLocalState();
    });
    _refreshBannerIfVisible();
  }

  Future<void> _openLogsPage() async {
    if (!mounted) return;
    await Navigator.of(context).push(
      MaterialPageRoute<void>(builder: (_) => const ApiLogsPage()),
    );
  }

  @override
  Widget build(BuildContext context) {
    final pages = <Widget>[
      _buildControlsPage(context),
      _buildCategoriesPage(context),
    ];
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
      body: pages[_pageIndex],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _pageIndex,
        onDestinationSelected: (int index) {
          setState(() => _pageIndex = index);
        },
        destinations: const <NavigationDestination>[
          NavigationDestination(icon: Icon(Icons.tune), label: 'Controls'),
          NavigationDestination(icon: Icon(Icons.category), label: 'Categories'),
        ],
      ),
    );
  }

  Widget _buildControlsPage(BuildContext context) {
    return SingleChildScrollView(
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
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Assistant ON/OFF'),
                subtitle: const Text('ON: banner + floating button. OFF: hide both.'),
                value: _assistantOn,
                onChanged: _setAssistantEnabled,
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: const Text('Enable AI Chips'),
                subtitle: const Text('Show/hide AI style/action chips in banner'),
                value: _aiChipsEnabled,
                onChanged: (bool value) {
                  setState(() {
                    _aiChipsEnabled = value;
                    _saveLocalState();
                  });
                  _refreshBannerIfVisible();
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
                  const Text('Cat rows:'),
                  const SizedBox(width: 8),
                  DropdownButton<int>(
                    value: _categoryRows,
                    items: const <DropdownMenuItem<int>>[
                      DropdownMenuItem(value: 1, child: Text('1')),
                      DropdownMenuItem(value: 2, child: Text('2')),
                      DropdownMenuItem(value: 3, child: Text('3')),
                    ],
                    onChanged: (int? value) {
                      if (value == null) return;
                      setState(() {
                        _categoryRows = value;
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
            ],
          ),
        ),
      );
  }

  Widget _buildCategoriesPage(BuildContext context) {
    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: <Widget>[
            Text('Static Comment Categories', style: Theme.of(context).textTheme.titleSmall),
            const SizedBox(height: 8),
            TextField(
              controller: _categoryNameController,
              decoration: const InputDecoration(
                labelText: 'Category name',
                hintText: 'e.g. Viral Reactions',
              ),
            ),
            const SizedBox(height: 8),
            TextField(
              controller: _categoryItemsController,
              decoration: const InputDecoration(
                labelText: 'Comma-separated comments',
                hintText: 'wow edit, this is clean, insane transitions',
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton(
                onPressed: _addStaticCategory,
                child: const Text('Add Static Category'),
              ),
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _categoryJsonController,
              minLines: 4,
              maxLines: 8,
              decoration: const InputDecoration(
                labelText: 'Bulk categories JSON',
                hintText: '[{"name":"Category","items":["item1","item2"]}]',
              ),
            ),
            const SizedBox(height: 8),
            Row(
              children: <Widget>[
                Expanded(
                  child: OutlinedButton(
                    onPressed: _copyCategoryJsonExample,
                    child: const Text('Copy JSON Example'),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: OutlinedButton(
                    onPressed: _addStaticCategoriesFromJson,
                    child: const Text('Add From JSON'),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),
            TextField(
              controller: _categoryBulkController,
              decoration: const InputDecoration(
                labelText: 'Bulk categories',
                hintText: 'cat1:item1,item2,cat2:item3,item4',
              ),
            ),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: OutlinedButton(
                onPressed: _addStaticCategoriesFromBulk,
                child: const Text('Add Bulk Categories'),
              ),
            ),
            ..._staticCategories.asMap().entries.map((entry) {
              final int index = entry.key;
              final StaticCategory category = entry.value;
              return Card(
                child: ListTile(
                  dense: true,
                  title: Text(category.name),
                  subtitle: Text(
                    category.items.join(', '),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  trailing: IconButton(
                    onPressed: () => _removeStaticCategory(index),
                    icon: const Icon(Icons.delete_outline),
                  ),
                ),
              );
            }),
          ],
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
