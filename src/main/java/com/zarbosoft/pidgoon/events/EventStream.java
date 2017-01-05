package com.zarbosoft.pidgoon.events;

import com.zarbosoft.pidgoon.Grammar;
import com.zarbosoft.pidgoon.internal.ParseContext;

import java.util.Map;

public class EventStream<O> {

	private final Position position = new Position();
	private final ParseContext context;
	private final Grammar grammar;

	public EventStream(
			final Grammar grammar,
			final String node,
			final Map<String, Object> callbacks,
			final Store store,
			final int errorHistoryLimit,
			final int uncertaintyLimit,
			final boolean dumpAmbiguity
	) {
		this.grammar = grammar;
		this.context = grammar.prepare(node, callbacks, store, errorHistoryLimit, uncertaintyLimit, dumpAmbiguity);
	}

	public EventStream(final ParseContext step, final Grammar grammar) {
		this.context = step;
		this.grammar = grammar;
	}

	public EventStream push(final Event e, final String s) {
		position.event = e;
		position.at = s;
		/*
		System.out.println(String.format(
				"%s\n%s\n\n",
				position,
				context.leaves.stream().map(l -> l.toString()).collect(Collectors.joining("\n"))
		));
		*/
		final ParseContext nextStep = grammar.step(context, position);
		return new EventStream<O>(nextStep, grammar);
	}

	public O finish() {
		return (O) grammar.finish(context);
	}

}
