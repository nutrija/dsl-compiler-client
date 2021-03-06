﻿using System;
using System.Collections.Generic;
using System.Threading;
using System.Threading.Tasks;
using Microsoft.VisualStudio.Text;
using Microsoft.VisualStudio.Text.Tagging;

namespace DDDLanguage
{
	internal sealed class DddTokenTagger : ITagger<DddTokenTag>
	{
		private readonly ITextBuffer Buffer;
		//TODO: replace with array
		private readonly Dictionary<SyntaxType, DddTokenTag> DddTags;
		private ITagSpan<DddTokenTag>[] Tags = new ITagSpan<DddTokenTag>[0];
		private volatile bool Invalidated = true;
		private readonly Timer Timer;

		internal DddTokenTagger(ITextBuffer buffer)
		{
			Buffer = buffer;
			DddTags = new Dictionary<SyntaxType, DddTokenTag>();
			DddTags[SyntaxType.Keyword] = new DddTokenTag(DddTokenTypes.Keyword);
			DddTags[SyntaxType.Identifier] = new DddTokenTag(DddTokenTypes.Identifier);
			DddTags[SyntaxType.StringQuote] = new DddTokenTag(DddTokenTypes.StringQuote);
			this.Timer = new System.Threading.Timer(_ => Task.Factory.StartNew(ParseAndCache), null, -1, -1);
			ParseAndCache();
			Buffer.Changed += (s, ea) =>
			{
				if (ea.After != Buffer.CurrentSnapshot)
					return;
				Invalidated = true;
				lock (Timer)
					Timer.Change(300, -1);
			};
		}

		private void ParseAndCache()
		{
			if (!Invalidated)
			{
				lock (Timer)
					Timer.Change(-1, -1);
				return;
			}
			try
			{
				Invalidated = false;
				var snapshot = Buffer.CurrentSnapshot;
				bool validDsl;
				var tokens = SyntaxParser.GetTokens(snapshot, out validDsl);
				var arr = new ITagSpan<DddTokenTag>[tokens.Length];
				int previousLine = -1;
				ITextSnapshotLine line = null;
				for (int i = 0; i < tokens.Length; i++)
				{
					var t = tokens[i];
					if (t.Line != previousLine)
					{
						line = snapshot.GetLineFromLineNumber(t.Line - 1);
						previousLine = t.Line;
					}
					var span = new SnapshotSpan(snapshot, new Span(line.Start.Position + t.Column, t.Value.Length));
					arr[i] = new TagSpan<DddTokenTag>(span, DddTags[t.Type]);
				}
				if (!validDsl)
				{
					if (TagsEqual(Tags, arr))
						return;
				}
				if (Invalidated)
					return;
				Tags = arr;
				TagsChanged(this, new SnapshotSpanEventArgs(new SnapshotSpan(snapshot, Span.FromBounds(0, snapshot.Length))));
				lock (Timer)
					Timer.Change(1000, -1);
			}
			catch
			{
				lock (Timer)
					Timer.Change(5000, -1);
			}
		}

		private static bool TagsEqual(ITagSpan<DddTokenTag>[] left, ITagSpan<DddTokenTag>[] right)
		{
			if (left.Length != right.Length)
				return false;
			for (int i = 0; i < left.Length; i++)
				if (!left[i].Equals(right[i]))
					return false;
			return true;
		}

		public event EventHandler<SnapshotSpanEventArgs> TagsChanged = (s, ea) => { };

		public IEnumerable<ITagSpan<DddTokenTag>> GetTags(NormalizedSnapshotSpanCollection collection)
		{
			return Tags;
		}
	}
}
