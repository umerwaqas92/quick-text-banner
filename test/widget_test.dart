import 'package:flutter_test/flutter_test.dart';
import 'package:tiktok_agent_android_flutter/main.dart';

void main() {
  testWidgets('app renders title', (WidgetTester tester) async {
    await tester.pumpWidget(const QuickTextBannerApp());
    expect(find.text('Quick Text Banner'), findsOneWidget);
  });
}
