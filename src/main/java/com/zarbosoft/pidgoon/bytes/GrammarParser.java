package com.zarbosoft.pidgoon.bytes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import com.google.common.collect.Range;
import com.zarbosoft.pidgoon.bytes.internal.Callback;
import com.zarbosoft.pidgoon.bytes.internal.Clip;
import com.zarbosoft.pidgoon.bytes.internal.ClipStore;
import com.zarbosoft.pidgoon.internal.Node;
import com.zarbosoft.pidgoon.nodes.Not;
import com.zarbosoft.pidgoon.nodes.Reference;
import com.zarbosoft.pidgoon.nodes.Repeat;
import com.zarbosoft.pidgoon.nodes.Sequence;
import com.zarbosoft.pidgoon.nodes.Union;

public class GrammarParser {
	public static Grammar parse(InputStream stream, Map<String, Callback> callbacks) throws IOException {
		Grammar g = new Grammar();
		g.add(
			"root", 
			new Repeat(
				new Sequence()
					.add(new Reference("interstitial"))
					.add(new Reference("rule"))));
		g.add(
			"rule", 
			new Capture(
				new Sequence()
					.add(new Reference("name"))
					.add(Terminal.fromChar(':'))
					.add(new Reference("interstitial"))
					.add(new Reference("expression"))
					.add(Terminal.fromChar(';').cut()),
				(store) -> {
					Node base = (Node) store.popStack();
					String name = (String) store.popStack();
					Grammar grammar = (Grammar) store.popStack();
					grammar.add(
						name, 
						new Capture(
							base, 
							callbacks.getOrDefault(
								name, 
								new Callback() {
									@Override
									public void accept(ClipStore store) {}
								}
							)
						)
					);
					store.pushStack(grammar);
				}
			)
		);
		g.add(
			"identifier", 
			new Sequence()
				.add(new Repeat(
						new Union()
							.add(new Terminal(Range.closed((byte)'a', (byte)'z')))
							.add(new Terminal(Range.closed((byte)'A', (byte)'Z')))
							.add(new Terminal(Range.closed((byte)'0', (byte)'9')))
							.add(Terminal.fromChar('_')))
						.min(1))
				.add(new Reference("interstitial")));
		g.add(
			"name", 
			new Capture(
				new Reference("identifier"),
				(store) -> {
					store.pushStack(store.topData().toString());
				}));
		g.add(
			"left_expression", 
			new Union()
				.add(new Sequence()
					.add(Terminal.fromChar('('))
					.add(new Reference("interstitial"))
					.add(new Reference("expression"))
					.add(Terminal.fromChar(')'))
					.add(new Reference("interstitial")))
				.add(new Reference("reference"))
				.add(new Reference("wildcard"))
				.add(new Reference("terminal"))
				.add(new Reference("drop"))
				.add(new Reference("not"))
				.add(new Reference("repone"))
				.add(new Reference("repmin"))
				.add(new Reference("rep"))
		);
		g.add(
			"expression", 
			new Union()
				.add(new Reference("left_expression"))
				.add(new Reference("sequence"))
				.add(new Reference("union")));
		g.add(
			"reference", 
			new Capture(
				new Reference("identifier"),
				(store) -> {
					store.pushStack(new Reference(store.topData().toString()));
				}));
		g.add(
			"union", 
			new Capture(
				new Sequence()
					.add(new Reference("left_expression"))
					.add(Terminal.fromChar('|'))
					.add(new Reference("interstitial"))
					.add(new Reference("expression")),
				(store) -> {
					Node right = (Node) store.popStack();
					Node left = (Node) store.popStack();
					store.pushStack(new Union().add(left).add(right));
				}));
		g.add(
			"sequence", 
			new Capture(
				new Sequence()
					.add(new Reference("left_expression"))
					.add(new Reference("interstitial1"))
					.add(new Reference("expression")),
				(store) -> {
					Node right = (Node) store.popStack();
					Node left = (Node) store.popStack();
					store.pushStack(new Sequence().add(left).add(right));
				}));
		g.add(
			"terminal_escape", 
			new Capture(
				new Sequence()
					.add(Terminal.fromChar('\\').drop())
					.add(new Wildcard()),
				(store) -> {
					// TODO \xNN escapes
					byte top = store.topData().dataFirst();
					if (top == (byte)'r') store.setData(new Clip((byte) '\r'));
					if (top == (byte)'n') store.setData(new Clip((byte) '\n'));
					if (top == (byte)'t') store.setData(new Clip((byte) '\t'));
				}));
		g.add(
			"wildcard", 
			new Capture(
				new Sequence()
					.add(Terminal.fromChar('.'))
					.add(new Reference("interstitial")),
				(store) -> { store.pushStack(new Wildcard()); }));
		g.add(
			"terminal", 
			new Capture(
				new Union()
					.add(new Sequence()
						.add(Terminal.fromChar('[').drop())
						.add(new Repeat(
							new Union()
								.add(new Not(Terminal.fromChar('\\', ']')))
								.add(new Reference("terminal_escape"))
						).min(1))
						.add(Terminal.fromChar(']').drop())
						.add(new Reference("interstitial"))
					)
					.add(new Sequence()
						.add(Terminal.fromChar('\'').drop())
						.add(new Union()
							.add(new Not(Terminal.fromChar('\\', '\'')))
							.add(new Reference("terminal_escape"))
						)
						.add(Terminal.fromChar('\'').drop())
						.add(new Reference("interstitial"))
					),
				(store) -> {
					store.pushStack(new Terminal(store.topData().dataRender()));
				}));
		g.add(
			"string", 
			new Capture(
				new Sequence()
					.add(Terminal.fromChar('"').drop())
					.add(new Repeat(
						new Union()
							.add(new Not(Terminal.fromChar('\\', '"')))
							.add(new Sequence()
								.add(Terminal.fromChar('\\').drop())
								.add(new Wildcard()))
					).min(1))
					.add(Terminal.fromChar('"').drop())
					.add(new Reference("interstitial")),
				(store) -> {
					store.pushStack(Grammar.byteSeq(store.topData().dataRender()));
				}));
		g.add(
			"drop", 
			new Capture(
				new Sequence()
					.add(Terminal.fromChar('#'))
					.add(new Reference("interstitial"))
					.add(new Reference("left_expression")),
				(store) -> {
					store.pushStack(((Node)store.popStack()).drop());
				}));
		g.add(
			"not", 
			new Capture(
				new Sequence()
					.add(Terminal.fromChar('~'))
					.add(new Reference("interstitial"))
					.add(new Reference("left_expression")),
				(store) -> {
					store.pushStack(new Not((Node)store.popStack()));
				}));
		g.add(
			"repone", 
			new Capture(
				new Sequence()
					.add(new Reference("left_expression"))
					.add(Terminal.fromChar('?'))
					.add(new Reference("interstitial")),
				(store) -> {
					store.pushStack(new Repeat((Node)store.popStack()).max(1));
				}));
		g.add(
			"repmin", 
			new Capture(
				new Sequence()
					.add(new Reference("left_expression"))
					.add(Terminal.fromChar('+'))
					.add(new Reference("interstitial")),
				(store) -> {
					store.pushStack(new Repeat((Node)store.popStack()).min(1));
				}));
		g.add(
			"rep", 
			new Capture(
				new Sequence()
					.add(new Reference("left_expression"))
					.add(Terminal.fromChar('*'))
					.add(new Reference("interstitial")),
				(store) -> {
					store.pushStack(new Repeat((Node)store.popStack()));
				}));
		g.add(
			"comment", 
			new Sequence()
				.add(Grammar.stringSeq("//"))
				.add(new Not(new Reference("eol")))
				.add(new Reference("eol")));
		g.add(
			"interstitial1", 
			new Repeat(
				new Union()
					.add(Terminal.fromChar(' ', '\t'))
					.add(new Reference("eol"))
					.add(new Reference("comment"))
				)
				.min(1)
				.drop()
		);
		g.add(
			"interstitial", 
			new Repeat(new Reference("interstitial1")).max(1)
		);
		g.add(
			"eol", 
			new Union()
				.add(new Sequence().add(new Terminal((byte)0x0D)).add(new Terminal((byte)0x0A)))
				.add(new Terminal((byte)0x0A))
				.drop());
		Grammar after = (Grammar) g.parse("root", stream, new Grammar());
		System.out.println(String.format("Final grammar:\n%s\n", after));
		return after;
	}

}